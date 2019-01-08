package com.example.TcpClient;

import java.util.Random;

import com.alibaba.fastjson.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.net.HttpURLConnection;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;

/**
 * Hello world!
 *
 */
public class App implements ITcpHandlerProc
{
    private static boolean isRun = false;
    private static App sInstance;
    private static String DEVICE_ID;
    private static Object mainLoop = new Object();
    private static String SERVER_URL = "http://49.4.95.51:8082/naturalbase";

    private class ExitHander extends Thread{
        @Override
        public void run(){
            isRun = false;
            synchronized(mainLoop){
                mainLoop.notify();
            }
            System.out.println("catch ctrl+c");
        }
    }
    
    public App(){
        try{
            Runtime.getRuntime().addShutdownHook(new ExitHander());
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    public static App instance(){
        if(sInstance == null){
            sInstance = new App();
        }
        return sInstance;
    }

    public static void main( String[] args )
    {
        if (args.length != 2){
            System.out.println("input param is not 2.");
            return;
        }

        if (!args[0].equals("-DEVICEID")){
            System.out.println("param is not equal -DEVICEID");
            return;
        }


        App.instance();

        DEVICE_ID = args[1];

        //String onLineMessage = "{\"MessageHeader\":{\"MessageType\":\"DeviceOnline\",\"RequestId\":\"unused\",\"DeviceId\":" + DEVICE_ID + "}}";
        byte[] onLineMessage = null;
        try{
            byte[] deviceId = DEVICE_ID.getBytes("UTF-8");
            onLineMessage = new byte[2+deviceId.length];
            onLineMessage[0] = (byte)0x55;
            onLineMessage[1] = (byte)deviceId.length;
            System.arraycopy(deviceId, 0, onLineMessage, 2, deviceId.length);
        }
        catch(Exception e){
            e.printStackTrace();
        }
        
        StringBuilder builder = new StringBuilder();
        builder.append("send: ");
        for (byte b:onLineMessage){
            builder.append(String.format( "0x%x ",  b));
        }
        System.out.println(builder);
        TcpClient client = new TcpClient(null);
        client.send(onLineMessage);

        isRun = true;
        int counter = 0;
        while(isRun){
            try{
                JSONObject msg = new JSONObject();
                msg.put("MessageHeader", App.instance().MakeupMessageHeader("Sync", "Unused", Integer.valueOf(DEVICE_ID)));
                msg.put("Message", App.instance().MakeupMessage());
                System.out.println("send message[" + counter + "]:" + msg.toJSONString());
                counter++;
                //client.send(msg.toJSONString());
                post(SERVER_URL, msg.toJSONString());
                synchronized(mainLoop){
                    mainLoop.wait(15000);
                }
            }
            catch(Exception e){
                e.printStackTrace();
                isRun = false;
            }
        }
        System.out.println("main loop break, application exit!");
    }

    private JSONObject MakeupMessageHeader(String messageType, String requestId, int deviceId) {
		JSONObject messageHeader = new JSONObject();
		messageHeader.put("MessageType", messageType);
		messageHeader.put("RequestId", requestId);
		messageHeader.put("DeviceId", deviceId);
		return messageHeader;
    }
    
    private JSONObject MakeupMessage(){
        JSONObject message = new JSONObject();
        message.put("DataItemSize", 2);

        JSONArray dataItemList = new JSONArray();

        JSONObject dataItem1 = new JSONObject();
        dataItem1.put("Key", "SINGLE_TEXT");
        dataItem1.put("Value", DEVICE_ID);
        dataItem1.put("TimeStamp", String.valueOf(GetCurrentTimeStamp()));
        dataItem1.put("DeleteBit", false);

        Random rand = new Random();
        JSONObject dataItem2 = new JSONObject();
        dataItem2.put("Key", "RANDOM_NUMBER");
        dataItem2.put("Value", String.valueOf(rand.nextGaussian()));
        dataItem2.put("TimeStamp", String.valueOf(GetCurrentTimeStamp()));
        dataItem2.put("DeleteBit", false);

        dataItemList.add(dataItem1);
        dataItemList.add(dataItem2);

        message.put("DataItem", dataItemList);

        return message;
    }

    public static long GetCurrentTimeStamp() {
		long currentTimeMs = System.currentTimeMillis();
		long currentNanoTime = System.nanoTime();
		final int K = 1000;
		final int M = K * K;
		long currentTimeUs = 0;
		
		currentTimeUs = currentTimeMs * K;
		currentTimeUs = currentTimeUs + (currentNanoTime-(currentNanoTime/M)*M)/K;
		return currentTimeUs;
	}

    public static void post(String url, String body) throws Exception {
        String encoding = "UTF-8";

        byte[] data = body.getBytes(encoding);
        URL PostUrl = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) PostUrl.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
     
        conn.setRequestProperty("Content-Type", "application/json; charset=" + encoding);
        conn.setRequestProperty("Content-Length", String.valueOf(data.length));
        conn.setConnectTimeout(5 * 1000);
        OutputStream outStream = conn.getOutputStream();
        outStream.write(data);
        outStream.flush();
        outStream.close();
        System.out.println(conn.getResponseCode()); // 响应代码 200表示成功
        if (conn.getResponseCode() == 200) {
            InputStream inStream = conn.getInputStream();
            String result = new String(toByteArray(inStream), "UTF-8");
            System.out.println(result); // 响应代码 200表示成功
        }
    }

    private static byte[] toByteArray(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
        }
        return output.toByteArray();
    }

    @Override
    public void onReceiveData(String data){
        System.out.println("receive message:" + data);
    }
}
