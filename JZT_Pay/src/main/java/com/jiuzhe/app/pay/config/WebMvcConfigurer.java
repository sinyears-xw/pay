package com.jiuzhe.app.pay.config;

import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.Configuration;
import com.jiuzhe.app.pay.utils.WebHookInterceptor;

@Configuration
public class WebMvcConfigurer extends WebMvcConfigurerAdapter {

	@Autowired
    private WebHookInterceptor webHookInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // registry.addInterceptor(webHookInterceptor).addPathPatterns("/forbidden/**");
        super.addInterceptors(registry);
    }

}
