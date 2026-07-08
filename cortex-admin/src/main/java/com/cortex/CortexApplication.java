package com.cortex;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 启动程序
 * 
 * @author cortex
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class CortexApplication
{
    public static void main(String[] args)
    {
         System.setProperty("spring.devtools.restart.enabled", "false");
        SpringApplication.run(CortexApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  CortexAI 启动成功   ლ(´ڡ`ლ)ﾞ  ");
    }
}
