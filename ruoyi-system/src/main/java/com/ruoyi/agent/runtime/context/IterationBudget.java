package com.ruoyi.agent.runtime.context;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 迭代预算 — 参考 hermes IterationBudget
 * 线程安全的 consume/refund 计数器
 *
 * @author ruoyi
 */
public class IterationBudget
{
    private final int maxTotal;
    private int used;
    private final ReentrantLock lock = new ReentrantLock();

    public IterationBudget(int maxTotal)
    {
        this.maxTotal = maxTotal;
        this.used = 0;
    }

    /**
     * 消耗一次迭代，返回是否允许
     */
    public boolean consume()
    {
        lock.lock();
        try
        {
            if (used >= maxTotal)
            {
                return false;
            }
            used++;
            return true;
        }
        finally
        {
            lock.unlock();
        }
    }

    /**
     * 退还一次迭代
     */
    public void refund()
    {
        lock.lock();
        try
        {
            if (used > 0)
            {
                used--;
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    /**
     * 重置(每轮对话开始时调用)
     */
    public void reset()
    {
        lock.lock();
        try
        {
            used = 0;
        }
        finally
        {
            lock.unlock();
        }
    }

    public int getUsed()
    {
        lock.lock();
        try
        {
            return used;
        }
        finally
        {
            lock.unlock();
        }
    }

    public int getRemaining()
    {
        lock.lock();
        try
        {
            return Math.max(0, maxTotal - used);
        }
        finally
        {
            lock.unlock();
        }
    }

    public int getMaxTotal() { return maxTotal; }
}
