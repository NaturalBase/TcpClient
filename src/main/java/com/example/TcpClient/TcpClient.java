package com.example.TcpClient;

import java.util.Queue;
import java.util.LinkedList;
import java.net.Socket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;

public class TcpClient extends Thread{
    private final String SERVER_IP = "49.4.95.51";
    private final int SERVER_PORT = 10087;

    private Object objLockSendQueue = new Object();
    private Queue<byte[]> sendQueue = new LinkedList<byte[]>();
    private Object objLockReceiveQueue = new Object();
    private Queue<byte[]> receiveQueue = new LinkedList<byte[]>();

    private Socket socket;
    private InputStream in;
    private OutputStream out;

    private boolean isRunning = false;
    private Object objLockRunning = new Object();

    private boolean isReceiveThreadRunning = false;
    private Object objLockReceiveThread = new Object();

    private ITcpHandlerProc tcpHandlerProc;

    private Thread receiveThread;

    private Timer HeartBeatTimer = new Timer();
    private TimerTask HeartBeatTimerProc = new TimerTask(){
        @Override
        public void run(){
            byte[] heartbeatMessage = new byte[]{(byte)0x5A, 0x0};
            send(heartbeatMessage);
        }
    };

    public TcpClient(ITcpHandlerProc handler){
        tcpHandlerProc = handler;
        this.start();
        HeartBeatTimer.schedule(HeartBeatTimerProc, 0, 10*1000);
    }

    private int BUFFER_SIZE = 4096;
    private byte[] buffer = new byte[BUFFER_SIZE];


    public void send(String data){
        synchronized(objLockSendQueue){
            try{
                sendQueue.offer(data.getBytes("UTF-8"));
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    public void send(byte[] data){
        synchronized(objLockSendQueue){
            sendQueue.offer(data);
        }
    }

    @Override
    public void run(){
        synchronized(objLockRunning){
            isRunning = true;
        }
        try{
            socket = new Socket(SERVER_IP, SERVER_PORT);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            receiveThread = new Thread(receiveThreadProc);
            receiveThread.start();
            while(isRunning){
                synchronized(objLockReceiveQueue){
                    if(!receiveQueue.isEmpty()){
                        byte[] message = receiveQueue.poll();
                        StringBuilder builder = new StringBuilder();

                        for (int i=0; i<message.length; i++){
                            builder.append(String.format("0x%x ", message[i]));
                        }
                        
                        if (tcpHandlerProc != null){
                            //tcpHandlerProc.onReceiveData(message);
                        }
                        else{
                            System.out.println("receive message:"+ builder);
                        }
                    }
                }
                synchronized(objLockSendQueue){
                    if(!sendQueue.isEmpty()){
                        byte[] msg = sendQueue.poll();
                        out.write(msg);
                        out.flush();
                    }
                }
            }
        }
        catch (IOException e){
            System.out.println("TclClient create socket fail.");
        }
        finally{
            try{
                System.out.println("TcpClient close socket.");
                in.close();
                out.close();
                socket.close();
            }
            catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    private Runnable receiveThreadProc = new Runnable() {
        public void run(){
            synchronized(objLockReceiveThread){
                isReceiveThreadRunning = true;
            }
            int len;
            try{
                while(isReceiveThreadRunning && (len = in.read(buffer)) != -1){
                    synchronized(objLockReceiveQueue){
                        byte[] receiveMsg = new byte[len];
                        System.arraycopy(buffer, 0, receiveMsg, 0, len);
                        String message = new String(buffer, 0, len, "UTF-8");
                        receiveQueue.offer(receiveMsg);
                    }
                }
            }
            catch (IOException e){
                System.out.println("TcpClient receiveThread catch exception.");
                e.printStackTrace();    
            }
        }
    };
}