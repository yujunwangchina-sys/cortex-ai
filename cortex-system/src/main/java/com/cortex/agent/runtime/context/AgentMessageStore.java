package com.cortex.agent.runtime.context;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.cortex.agent.domain.AiAgentMessage;
import com.cortex.agent.mapper.AiAgentMessageMapper;
import com.cortex.agent.runtime.model.ChatMessage;
import com.cortex.common.core.redis.RedisCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Agent message store: Redis hot cache + incremental DB persistence.
 *
 * <p>Redis acts as the hot layer (List per session, TTL 60 min). Only NEW
 * messages are batch-inserted into ai_agent_message each round, avoiding
 * the full-rewrite cost of the old JSON-blob approach.
 *
 * <p>Key scheme: {@code agent:msg:{sessionId}} — a Redis List whose elements
 * are JSON objects (ChatMessage.toJson()).
 *
 * @author cortex
 */
@Component
public class AgentMessageStore
{
    private static final Logger log = LoggerFactory.getLogger(AgentMessageStore.class);

    private static final String KEY_PREFIX = "agent:msg:";
    private static final int REDIS_TTL_MINUTES = 60;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private AiAgentMessageMapper messageMapper;

    // ------------------------------------------------------------------
    //  Load
    // ------------------------------------------------------------------

    /**
     * Load all messages for a session: Redis first, DB fallback.
     * If the Redis key is missing (cold start), messages are loaded from
     * the database and back-filled into Redis.
     *
     * @return full message list (empty if none)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<ChatMessage> loadMessages(String sessionId)
    {
        String key = key(sessionId);

        // --- Redis hit ---
        if (Boolean.TRUE.equals(redisCache.hasKey(key)))
        {
            List raw = redisCache.getCacheList(key);
            if (raw != null && !raw.isEmpty())
            {
                List<ChatMessage> messages = new ArrayList<>(raw.size());
                for (Object item : raw)
                {
                    JSONObject obj = coerceToJson(item);
                    if (obj != null)
                    {
                        messages.add(ChatMessage.fromJson(obj));
                    }
                }
                log.debug("Loaded {} messages from Redis [session={}]", messages.size(), sessionId);
                return messages;
            }
        }

        // --- DB fallback ---
        List<AiAgentMessage> entities = messageMapper.selectBySessionId(sessionId);
        List<ChatMessage> messages = new ArrayList<>(entities.size());
        for (AiAgentMessage e : entities)
        {
            messages.add(toChatMessage(e));
        }

        // Back-fill Redis so subsequent reads are fast
        if (!messages.isEmpty())
        {
            cacheToRedis(sessionId, messages);
        }

        log.debug("Loaded {} messages from DB [session={}]", messages.size(), sessionId);
        return messages;
    }

    // ------------------------------------------------------------------
    //  Persist (incremental)
    // ------------------------------------------------------------------

    /**
     * Flush only the new messages (from {@code fromSeq} onward) to both
     * Redis (append) and the database (batch insert).
     *
     * @param sessionId   session ID
     * @param allMessages full in-memory message list
     * @param fromSeq     index of the first un-persisted message
     * @param createBy    creator (user login name)
     * @return new fromSeq (= allMessages.size())
     */
    public int flushNewMessages(String sessionId, List<ChatMessage> allMessages,
                                 int fromSeq, String createBy)
    {
        if (fromSeq >= allMessages.size())
        {
            return fromSeq; // nothing new
        }

        int count = allMessages.size() - fromSeq;
        String key = key(sessionId);

        // --- Compression recovery: if fromSeq == 0, old Redis/DB data is stale ---
        if (fromSeq == 0)
        {
            redisCache.deleteObject(key);
            try
            {
                messageMapper.deleteBySessionId(sessionId);
            }
            catch (Exception e)
            {
                log.warn("Compression cleanup failed [session={}]", sessionId, e);
            }
        }

        // --- Build entities ---
        List<ChatMessage> newMessages = new ArrayList<>(allMessages.subList(fromSeq, allMessages.size()));
        List<AiAgentMessage> entities = new ArrayList<>(count);
        Object[] jsonObjs = new Object[count];
        for (int i = 0; i < count; i++)
        {
            ChatMessage msg = newMessages.get(i);
            jsonObjs[i] = msg.toJson();
            AiAgentMessage entity = toEntity(sessionId, fromSeq + i, msg);
            if (createBy != null)
            {
                entity.setCreateBy(createBy);
            }
            entities.add(entity);
        }

        // --- DB first (durable): if this fails, do NOT update seq ---
        try
        {
            messageMapper.batchInsertMessages(entities);
        }
        catch (Exception e)
        {
            log.error("Batch insert messages failed [session={}, count={}]", sessionId, count, e);
            return fromSeq; // keep old seq; will retry on next persist
        }

        // --- Redis second (hot cache): safe to append now that DB has the data ---
        redisCache.redisTemplate.opsForList().rightPushAll(key, jsonObjs);
        redisCache.expire(key, REDIS_TTL_MINUTES, TimeUnit.MINUTES);

        log.debug("Flushed {} new messages [session={}, fromSeq={}]", count, sessionId, fromSeq);
        return allMessages.size();
    }

    // ------------------------------------------------------------------
    //  Lifecycle
    // ------------------------------------------------------------------

    /**
     * Initialize a fresh Redis key for a new session.
     */
    public void initSession(String sessionId)
    {
        String key = key(sessionId);
        redisCache.deleteObject(key); // clear any stale data
        redisCache.expire(key, REDIS_TTL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Delete all messages for a session (Redis + DB).
     */
    public void deleteSession(String sessionId)
    {
        redisCache.deleteObject(key(sessionId));
        try
        {
            messageMapper.deleteBySessionId(sessionId);
        }
        catch (Exception e)
        {
            log.warn("Delete DB messages failed [session={}]", sessionId, e);
        }
    }

    /**
     * Close session: Redis key will expire naturally via TTL.
     * All messages are already persisted to DB via flushNewMessages.
     */
    public void closeSession(String sessionId)
    {
        // Optionally shorten TTL so Redis memory is freed sooner
        redisCache.expire(key(sessionId), 5, TimeUnit.MINUTES);
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private String key(String sessionId)
    {
        return KEY_PREFIX + sessionId;
    }

    @SuppressWarnings("unchecked")
    private void cacheToRedis(String sessionId, List<ChatMessage> messages)
    {
        String key = key(sessionId);
        Object[] objs = new Object[messages.size()];
        for (int i = 0; i < messages.size(); i++)
        {
            objs[i] = messages.get(i).toJson();
        }
        redisCache.redisTemplate.opsForList().rightPushAll(key, objs);
        redisCache.expire(key, REDIS_TTL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Coerce a deserialized Redis element into a JSONObject.
     * With FastJson2JsonRedisSerializer(Object.class), JSON objects come
     * back as JSONObject; but defensive handling covers Map/String too.
     */
    private JSONObject coerceToJson(Object item)
    {
        if (item == null) return null;
        if (item instanceof JSONObject) return (JSONObject) item;
        if (item instanceof String)
        {
            return JSON.parseObject((String) item);
        }
        // Last resort: round-trip through JSON
        return JSON.parseObject(JSON.toJSONString(item));
    }

    private AiAgentMessage toEntity(String sessionId, int seqNum, ChatMessage msg)
    {
        AiAgentMessage entity = new AiAgentMessage();
        entity.setSessionId(sessionId);
        entity.setSeqNum(seqNum);
        entity.setRole(msg.getRole());
        entity.setContent(msg.getContent());
        entity.setToolCallId(msg.getToolCallId());
        entity.setName(msg.getName());

        if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty())
        {
            JSONArray arr = new JSONArray();
            for (ChatMessage.ToolCall tc : msg.getToolCalls())
            {
                arr.add(tc.toJson());
            }
            entity.setToolCallsJson(arr.toJSONString());
        }

        if (msg.getImageUrls() != null && !msg.getImageUrls().isEmpty())
        {
            entity.setImageUrlsJson(JSON.toJSONString(msg.getImageUrls()));
        }

        entity.setCreateTime(new Date());
        return entity;
    }

    private ChatMessage toChatMessage(AiAgentMessage entity)
    {
        ChatMessage msg = new ChatMessage();
        msg.setRole(entity.getRole());
        msg.setContent(entity.getContent());
        msg.setToolCallId(entity.getToolCallId());
        msg.setName(entity.getName());

        if (entity.getToolCallsJson() != null && !entity.getToolCallsJson().isEmpty())
        {
            JSONArray arr = JSON.parseArray(entity.getToolCallsJson());
            List<ChatMessage.ToolCall> toolCalls = new ArrayList<>();
            for (Object item : arr)
            {
                toolCalls.add(ChatMessage.ToolCall.fromJson((JSONObject) item));
            }
            msg.setToolCalls(toolCalls);
        }

        if (entity.getImageUrlsJson() != null && !entity.getImageUrlsJson().isEmpty())
        {
            List<String> urls = JSON.parseArray(entity.getImageUrlsJson(), String.class);
            msg.setImageUrls(urls);
        }

        return msg;
    }
}
