package com.Firefire.paiban.config;

import com.Firefire.paiban.interceptor.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    @Value("${upload.dir:uploads}")
    private String uploadDir;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 本地开发环境 - 允许所有来源
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

        // 服务器生产环境 - 限制为具体域名(部署时取消下方注释，注释上方本地配置)
        // registry.addMapping("/**")
        //         .allowedOrigins("http://your-server-ip")
        //         .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        //         .allowedHeaders("*")
        //         .allowCredentials(true)
        //         .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 附件上传目录映射：/uploads/** → 配置的 upload.dir 目录
        File dir = new File(uploadDir);
        if (!dir.isAbsolute()) {
            // 相对路径基于后端工作目录解析，兼容 IDEA 与服务器部署
            dir = new File(System.getProperty("user.dir"), uploadDir);
        }
        String location = dir.getAbsolutePath().replace("\\", "/");
        if (!location.endsWith("/")) location += "/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + location);
    }
}
