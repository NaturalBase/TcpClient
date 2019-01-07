package com.example.TcpClient;

import java.util.Queue;
import java.util.LinkedList;
import java.net.Socket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class TcpClient extends Thread{
    private final String SERVER_IP = "49.4.95.51";
    private final int SERVER_PORT = 10086;

    private Object objLockSendQueue = new Object();
    private Queue<String> sendQueue = new LinkedList<String>();
    private Object objLockReceiveQueue = new Object();
    private Queue<String> receiveQueue = new LinkedList<String>();

    private Socket socket;
    private InputStream in;
    private OutputStream out;

    private boolean isRunning = false;
    private Object objLockRunning = new Object();

    private boolean isReceiveThreadRunning = false;
    private Object objLockReceiveThread = new Object();

    private ITcpHandlerProc tcpHandlerProc;

    private Thread receiveThread;

    public TcpClient(ITcpHandlerProc handler){
        tcpHandlerProc = handler;
        this.start();
    }

    private int BUFFER_SIZE = 4096;
    private byte[] buffer = new byte[BUFFER_SIZE];


    public void send(String data){
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
                        String msg = receiveQueue.poll();
                        if (tcpHandlerProc != null){
                            tcpHandlerProc.onReceiveData(msg);
                        }
                        else{
                            System.out.println("receive message:"+msg);
                        }
                    }
                }
                synchronized(objLockSendQueue){
                    if(!sendQueue.isEmpty()){
                        String msg = sendQueue.poll();
                        out.write(msg.getBytes());
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
                        String message = new String(buffer, 0, len, "UTF-8");
                        receiveQueue.offer(message);
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