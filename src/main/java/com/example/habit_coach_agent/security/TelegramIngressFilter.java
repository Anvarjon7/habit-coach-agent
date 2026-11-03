package com.example.habit_coach_agent.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;

/** Authenticate before reading any payload, then cap bytes before MVC deserializes them. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE+10)
@ConditionalOnProperty(name="telegram.enabled",havingValue="true")
public class TelegramIngressFilter extends OncePerRequestFilter {
    private static final int LIMIT=65536;
    private final byte[] secret;
    public TelegramIngressFilter(@Value("${telegram.webhook-secret:${TELEGRAM_WEBHOOK_SECRET:}}") String configured) {
        if(configured.length()<32 || !configured.matches("[A-Za-z0-9_-]{32,256}"))
            throw new IllegalStateException("Telegram webhook requires a 32-256 character secret");
        secret=configured.getBytes(StandardCharsets.UTF_8);
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String path=request.getServletPath();
        if(path==null || path.isEmpty()) path=request.getRequestURI().substring(request.getContextPath().length());
        path=path.replaceAll(";[^/]*","").replaceAll("/{2,}","/");
        if(!path.equals("/telegram/webhook")) { chain.doFilter(request,response); return; }
        var headers=Collections.list(request.getHeaders("X-Telegram-Bot-Api-Secret-Token"));
        if(headers.size()!=1 || headers.getFirst().length()>256 || !MessageDigest.isEqual(secret,headers.getFirst().getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(401); return;
        }
        if(!request.getMethod().equals("POST")) { response.setStatus(405); return; }
        if(request.getContentLengthLong()>LIMIT) { response.setStatus(413); return; }
        byte[] body=request.getInputStream().readNBytes(LIMIT+1);
        if(body.length>LIMIT) { response.setStatus(413); return; }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public int getContentLength() { return body.length; }
            @Override public long getContentLengthLong() { return body.length; }
            @Override public ServletInputStream getInputStream() {
                var input=new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public int read(byte[] target,int offset,int length) { return input.read(target,offset,length); }
                    @Override public boolean isFinished() { return input.available()==0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Synchronous webhook only"); }
                };
            }
            @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8)); }
        },response);
    }
}
