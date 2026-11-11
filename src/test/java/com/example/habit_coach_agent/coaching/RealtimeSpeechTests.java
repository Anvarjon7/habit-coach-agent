package com.example.habit_coach_agent.coaching;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class RealtimeSpeechTests {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private CoachingProperties config() {
        CoachingProperties config=new CoachingProperties(); config.setVoice("coral"); return config;
    }
    private static class Socket implements WebSocket {
        String sent; boolean aborted; long requests;
        public CompletableFuture<WebSocket> sendText(CharSequence text,boolean last) { sent=text.toString(); return CompletableFuture.completedFuture(this); }
        public CompletableFuture<WebSocket> sendBinary(ByteBuffer data,boolean last) { return CompletableFuture.completedFuture(this); }
        public CompletableFuture<WebSocket> sendPing(ByteBuffer data) { return CompletableFuture.completedFuture(this); }
        public CompletableFuture<WebSocket> sendPong(ByteBuffer data) { return CompletableFuture.completedFuture(this); }
        public CompletableFuture<WebSocket> sendClose(int status,String reason) { return CompletableFuture.completedFuture(this); }
        public void request(long n) { requests+=n; }
        public String getSubprotocol() { return ""; }
        public boolean isOutputClosed() { return aborted; }
        public boolean isInputClosed() { return aborted; }
        public void abort() { aborted=true; }
    }
    private void event(RealtimeSpeechGenerator.Exchange exchange,Socket socket,Map<String,Object> fields) {
        exchange.onText(socket,JSON.writeValueAsString(fields),true);
    }
    private void start(RealtimeSpeechGenerator.Exchange exchange,Socket socket) {
        event(exchange,socket,Map.of("type","session.created"));
        event(exchange,socket,Map.of("type","response.created","response",Map.of("id","r1")));
    }
    private void part(RealtimeSpeechGenerator.Exchange exchange,Socket socket,String type,String field,String value) {
        var fields=new HashMap<String,Object>(); fields.put("type",type); fields.put("response_id","r1");
        fields.put("item_id","i1"); fields.put("output_index",0); fields.put("content_index",0);
        if(field!=null) fields.put(field,value); event(exchange,socket,fields);
    }
    private void done(RealtimeSpeechGenerator.Exchange exchange,Socket socket,String status) {
        event(exchange,socket,Map.of("type","response.done","response",Map.of("id","r1","status",status)));
    }
    private void audio(RealtimeSpeechGenerator.Exchange exchange,Socket socket,String transcript) {
        part(exchange,socket,"response.output_audio.delta","delta",Base64.getEncoder().encodeToString(new byte[]{1,2,3,4}));
        part(exchange,socket,"response.output_audio.done",null,null);
        part(exchange,socket,"response.output_audio_transcript.done","transcript",transcript);
    }
    @Test void requestsIsolatedAudioResponseAndAssemblesMatchingTranscript() {
        var exchange=new RealtimeSpeechGenerator.Exchange(config(),JSON,"Try one page."); var socket=new Socket();
        start(exchange,socket);
        var request=JSON.readTree(socket.sent).path("response");
        assertEquals("none",request.path("conversation").asString());
        assertEquals("audio",request.path("output_modalities").get(0).asString());
        assertEquals("audio/pcm",request.path("audio").path("output").path("format").path("type").asString());
        assertEquals(24000,request.path("audio").path("output").path("format").path("rate").asInt());
        assertEquals(0,request.path("tools").size());
        assertEquals(4096,request.path("max_output_tokens").asInt());
        assertEquals("Try one page.",request.path("input").get(0).path("content").get(0).path("text").asString());
        audio(exchange,socket,"Try one page!"); done(exchange,socket,"completed");
        assertArrayEquals(new byte[]{1,2,3,4},exchange.result.join());
    }
    @Test void refusesMismatchedMissingOrIncompleteSpeech() {
        for(String scenario:List.of("mismatch","missing","incomplete")) {
            var exchange=new RealtimeSpeechGenerator.Exchange(config(),JSON,"Read one page."); var socket=new Socket(); start(exchange,socket);
            if(!scenario.equals("missing")) audio(exchange,socket,scenario.equals("mismatch")?"Read ten pages.":"Read one page.");
            done(exchange,socket,scenario.equals("incomplete")?"incomplete":"completed");
            assertTrue(exchange.result.isCompletedExceptionally()); assertTrue(socket.aborted);
        }
    }
    @Test void boundsFragmentedFramesAndDecodedAudio() {
        var exchange=new RealtimeSpeechGenerator.Exchange(config(),JSON,"hi"); var socket=new Socket();
        exchange.onText(socket,"a".repeat(100000),false); exchange.onText(socket,"a".repeat(40000),false);
        assertTrue(socket.aborted); assertTrue(exchange.result.isCompletedExceptionally());
        var properties=config(); properties.setMaxResponseBytes(32);
        exchange=new RealtimeSpeechGenerator.Exchange(properties,JSON,"hi"); socket=new Socket(); start(exchange,socket);
        part(exchange,socket,"response.output_audio.delta","delta",Base64.getEncoder().encodeToString(new byte[34]));
        assertTrue(exchange.result.isCompletedExceptionally()); assertTrue(socket.aborted);
    }
    @Test void rejectsWrongResponseAndDuplicateSessionWithoutRetry() {
        var exchange=new RealtimeSpeechGenerator.Exchange(config(),JSON,"hi"); var socket=new Socket(); start(exchange,socket);
        event(exchange,socket,Map.of("type","response.output_audio.delta","response_id","other","delta","AQI=","item_id","i1","content_index",0,"output_index",0));
        assertTrue(exchange.result.isCompletedExceptionally());
        exchange=new RealtimeSpeechGenerator.Exchange(config(),JSON,"hi"); socket=new Socket(); start(exchange,socket);
        event(exchange,socket,Map.of("type","session.created")); assertTrue(exchange.result.isCompletedExceptionally());
    }
    @Test void mapsProviderErrorsWithoutExposingTheirContents() {
        var exchange=new RealtimeSpeechGenerator.Exchange(config(),JSON,"hi"); var socket=new Socket();
        event(exchange,socket,Map.of("type","error","error",Map.of("code","rate_limit_exceeded","message","SECRET")));
        var error=assertThrows(CompletionException.class,exchange.result::join);
        assertEquals(CoachProviderException.Reason.BUSY,((CoachProviderException)error.getCause()).getReason());
        assertFalse(error.toString().contains("SECRET")); assertTrue(socket.aborted);
    }
    @Test void transcriptNormalizationPreservesNumbersAndNegation() {
        assertEquals(RealtimeSpeechGenerator.normalize("Read one page."),RealtimeSpeechGenerator.normalize(" read  one page! "));
        assertNotEquals(RealtimeSpeechGenerator.normalize("-5"),RealtimeSpeechGenerator.normalize("5"));
        assertNotEquals(RealtimeSpeechGenerator.normalize("1.5"),RealtimeSpeechGenerator.normalize("15"));
        assertNotEquals(RealtimeSpeechGenerator.normalize("don't"),RealtimeSpeechGenerator.normalize("dont"));
    }
    @Test void closedExchangeNeverSendsLateRequestOrPublishesPartialAudio() {
        var exchange=new RealtimeSpeechGenerator.Exchange(config(),JSON,"hi"); var socket=new Socket();
        exchange.closed=true; exchange.onOpen(socket);
        event(exchange,socket,Map.of("type","session.created"));
        assertTrue(socket.aborted); assertNull(socket.sent); assertFalse(exchange.result.isDone());
        exchange=new RealtimeSpeechGenerator.Exchange(config(),JSON,"hi"); socket=new Socket(); start(exchange,socket);
        part(exchange,socket,"response.output_audio.delta","delta","AQI=");
        exchange.onClose(socket,1000,"private reason");
        assertTrue(exchange.result.isCompletedExceptionally());
    }
    @Test void missingEncoderFailsWithoutLeakingPath() {
        var error=assertThrows(CoachProviderException.class,()->new OpusEncoder().encode(new byte[2400],"missing-private-executable",1024,Duration.ofSeconds(1)));
        assertFalse(error.toString().contains("private"));
    }
    @Test void realEncoderProducesOpusWhenAvailable() throws Exception {
        String executable=System.getProperty("coach.test.ffmpeg","ffmpeg");
        boolean required=Boolean.getBoolean("coach.test.require-ffmpeg");
        try {
            Process probe=new ProcessBuilder(executable,"-version").redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            boolean available=probe.waitFor(5,TimeUnit.SECONDS) && probe.exitValue()==0; probe.destroyForcibly();
            if(required) assertTrue(available,"ffmpeg is required");
            org.junit.jupiter.api.Assumptions.assumeTrue(available,"ffmpeg unavailable");
        } catch(java.io.IOException unavailable) {
            if(required) fail("ffmpeg is required");
            org.junit.jupiter.api.Assumptions.abort("ffmpeg unavailable");
        }
        byte[] encoded=new OpusEncoder().encode(new byte[24000*2],executable,100000,Duration.ofSeconds(5));
        String beginning=new String(encoded,0,Math.min(512,encoded.length),StandardCharsets.ISO_8859_1);
        assertTrue(beginning.startsWith("OggS")); assertTrue(beginning.contains("OpusHead"));
    }
}
