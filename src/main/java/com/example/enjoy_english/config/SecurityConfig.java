package com.example.enjoy_english.config;

import com.example.enjoy_english.model.Log;
import com.example.enjoy_english.model.User;
import com.example.enjoy_english.service.LogService;
import com.example.enjoy_english.service.UserService;
import com.example.enjoy_english.tools.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.util.Date;

@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    @Resource
    private UserService userService;
    @Resource
    private LogService logService;

    private Log log = new Log();

    // 密码加密方式：由于项目要求这里不对密码进行加密
    @Bean
    PasswordEncoder passwordEncoder(){
        return NoOpPasswordEncoder.getInstance();
    }

    @Bean
    public CustomUserService customUserService() {
        return new CustomUserService();
    }

    @Bean
    LoginFilter loginFilter() throws Exception {
        LoginFilter loginFilter = new LoginFilter();

        //登录成功回调
        loginFilter.setAuthenticationSuccessHandler(new AuthenticationSuccessHandler() {
            @Override
            public void onAuthenticationSuccess(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Authentication authentication) throws IOException, ServletException {
                //记录登录Log
                User user = userService.findByAccno(authentication.getName());
                user.setPassword(null);
                String mac_address = (String) httpServletRequest.getSession().getAttribute("mac_address");
                log.setAccno(user.getAccno());
                log.setMac_address(mac_address);
                log.setLogindatetime(new Timestamp(new Date().getTime()));
                logService.add(log);
                //向前端返回信息
                httpServletResponse.setContentType("application/json;charset=utf-8");
                PrintWriter out = httpServletResponse.getWriter();
                out.write(new ObjectMapper().writeValueAsString(new Result(1, "登录成功", user)));
            }
        });

        //登录失败回调
        loginFilter.setAuthenticationFailureHandler(new AuthenticationFailureHandler() {
            @Override
            public void onAuthenticationFailure(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, AuthenticationException e) throws IOException, ServletException {
                Result result = new Result();
                result.setStatus(0);
                if (e instanceof BadCredentialsException){
                    result.setMessage("用户名或密码输入错误，请重新输入");
                } else {
                    result.setMessage(e.getMessage());
                }
                httpServletResponse.setContentType("application/json;charset=utf-8");
                PrintWriter out = httpServletResponse.getWriter();
                out.write(new ObjectMapper().writeValueAsString(result));
            }
        });
        loginFilter.setAuthenticationManager(authenticationManagerBean());

        //登录请求接口与参数名
        loginFilter.setFilterProcessesUrl("/login");
        loginFilter.setUsernameParameter("accno");
        loginFilter.setPasswordParameter("password");

        return loginFilter;
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws  Exception{
        auth.userDetailsService(customUserService()).passwordEncoder(passwordEncoder());
    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        // 配置不拦截的接口
        web.ignoring().antMatchers("/js/**", "/css/**", "/images/**");

    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
                .antMatchers("/").permitAll()   //"/"路径下的接口不需要权限
                .antMatchers("/management/**").hasRole("ADMIN") //"/management/**"路径下的接口需要有管理员权限
                .antMatchers("/api/**").hasAnyRole("ADMIN", "USER")
                .and()
                .logout()
                .logoutUrl("/logout")   // 注销登录url
                .logoutSuccessHandler((req, resp, authentication) -> {
                    log.setLogoutdatetime(new Timestamp(new Date().getTime()));
                    logService.add(log);
                    resp.setContentType("application/json;charset=utf-8");
                    PrintWriter out = resp.getWriter();
                    out.write(new ObjectMapper().writeValueAsString(new Result(1, "注销登录成功", null)));
                })
                .and()
                .csrf().disable()
                .exceptionHandling()
                .authenticationEntryPoint((req, resp, exception) -> {
                    resp.setContentType("application/json;charset=utf-8");
                    PrintWriter out = resp.getWriter();
                    out.write(new ObjectMapper().writeValueAsString(new Result(0, "用户未登录", null)));
                });
        http.addFilterAfter(loginFilter(), UsernamePasswordAuthenticationFilter.class);
    }
}
