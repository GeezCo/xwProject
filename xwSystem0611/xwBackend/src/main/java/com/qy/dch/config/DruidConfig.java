package com.qy.dch.config;


import com.alibaba.druid.support.http.StatViewServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Druid数据库连接池监控配置类
 * <p>
 * 注册Druid的StatViewServlet，开启Druid内置的SQL监控页面，
 * 访问路径为 /druid/*，需要登录认证。
 * 可用于监控SQL执行情况、慢查询、连接池状态等。
 * </p>
 */
@Configuration
public class DruidConfig {

    /**
     * 注册Druid监控页面的Servlet
     * 映射路径 /druid/*，配置登录用户名和密码
     *
     * @return Druid StatViewServlet的注册Bean
     */
    @Bean
    public ServletRegistrationBean<StatViewServlet> druidStatViewServlet() {
        ServletRegistrationBean<StatViewServlet> registrationBean =
                new ServletRegistrationBean<>(new StatViewServlet(), "/druid/*");

        // 配置登录账号密码（与 application.yml 一致）
        registrationBean.addInitParameter("loginUsername", "admin");
        registrationBean.addInitParameter("loginPassword", "admin");
        registrationBean.addInitParameter("allow", ""); // 允许所有IP访问（生产环境建议限制IP）

        return registrationBean;
    }
}
