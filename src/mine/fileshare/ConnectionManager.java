package mine.fileshare;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.UUID;


/**
 * ConnectionManager���������豸֮������ӣ�
 * ����������豸���͹�������������
 * �������豸�������������
 */
public class ConnectionManager {
	private static final String NAME = "FileShare";
	//private static final UUID MY_UUID = UUID.fromString("0024589A-B724-A78D-2EEA-E3E344FF6929");
	//private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	private static final UUID MY_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
	private BluetoothAdapter bluetoothAdapter;
	private Handler handler;
	private ServerThread serverThread;
	
	/*���³��������������ģ���������Ӳ���*/
	public static final String SERVERLOCALIP = "10.0.2.2";/*����ip���൱������ģ������ip*/
	public static int listenPort;/*�����豸��Ҫ����豸����ʱ��Ҫ���Ӵ˶˿�*/
	private EmulatorServerThread emulatorServerThread;/*�������̣߳����������豸����������*/
	
	public ConnectionManager(Context context, Handler handler,int port){
		this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		this.handler = handler;
		Log.i("fileshare","listenport:"+port);
		listenPort = port;
	}
	
	/*
	 * ����severThread����ʼ������������
	 */
	public void start(){		
		if(FileShare.ISEMULATOR){
			Log.i("FileShare","fileshare:connection manager in emulatorserver start");
			if(emulatorServerThread == null){
				emulatorServerThread = new EmulatorServerThread();
				emulatorServerThread.start();
			}
		}
		else{
			if(serverThread == null){
				serverThread = new ServerThread();
				serverThread.start();
			}
			if(emulatorServerThread == null){
				Log.i("fileshare","fileshare:also start emulatorserver");
				emulatorServerThread = new EmulatorServerThread();
				emulatorServerThread.start();
			}
		}			
	}
	
	/*
	 * ֹͣ������������
	 */
	public void stop(){
		if(serverThread != null)
			serverThread.cancel();
		if(emulatorServerThread != null)
			emulatorServerThread.cancel();
	}
	
	
	
	/*
	 * ��һ���豸������������
	 */
	public void connect(BluetoothDevice device) {       
		ClientThread clientThread = new ClientThread(device);
		clientThread.start();
    }
	
	/*
	 *  ��ģ��������
	 */
	public void connectEmulator(int serverPort){
		EmulatorClientThread emulatorClientThread = new EmulatorClientThread(serverPort);
		emulatorClientThread.start();
	}
	
	/*
	 * ���߳��������������豸������������󲢽�������
	 * ��������ģ������
	 */
	private class ServerThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        public ServerThread() {
            BluetoothServerSocket tmp = null;

            try {
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
            } catch (IOException e) {
                Log.i("FileShare_ConnectionManager", "can not get BluetoothServerSocket");
            }
            serverSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;

            /*������������һ������������㽨��һ������*/
            while (true) {
                try {                   
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    Log.i("FileShare_ConnectionManager_ServerThread", "accept fail");
                    break;
                }
                /*���ӳɹ����������淢�͡����ӳɹ���,��������Ӧ��BluetoothSocket*/
                if (socket != null) {
                	Message msg = handler.obtainMessage(FileShare.MESSAGE_CONNECTION_SUCCESS);
    	            HashMap<String,Object> data = new HashMap<String,Object>();
    	            data.put("device", socket.getRemoteDevice());
    	            data.put("socket", socket);
                	msg.obj = data;
                	handler.sendMessage(msg);
                }
            }
        }

        public void cancel() {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.i("FileShare_ConnectionManager", "can not close serverSocket");
            }
        }
    }
	
	/*
	 * ���߳������������豸�����������󲢽�������
	 * ��������ģ������
	 */
	 private class ClientThread extends Thread{
		 BluetoothSocket socket;
		 BluetoothDevice device;
		 
		 public ClientThread(BluetoothDevice device) {
	            this.device = device;
	            BluetoothSocket tmp = null;

	            /*��ָ���豸��ȡһ���������ӵ�BluetoothSocket*/
	            try {
	                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
	            } catch (IOException e) {
	                Log.i("FileShare_ConnectionManager","can not get bluetooth socket from"+device.getName());
	            }
	            socket = tmp;
	        }
		 
	     public void run(){
	    		bluetoothAdapter.cancelDiscovery();
	    		
	    		try {	                
	                socket.connect();
	            } catch (IOException e) {
	                /*�������淢�͡�����ʧ�ܡ�*/
	            	Message msg = handler.obtainMessage(FileShare.MESSAGE_CONNECTION_FAIL);
	            	msg.obj = device;
	            	handler.sendMessage(msg);
	                try {
	                    socket.close();
	                } catch (IOException e2) {                  
	                }
	              
	                return;
	            }
	            /*�������淢�͡����ӳɹ�������������Ӧ��BluetoothSocket*/
	            Message msg = handler.obtainMessage(FileShare.MESSAGE_CONNECTION_SUCCESS);
	            HashMap<String,Object> data = new HashMap<String,Object>();
	            data.put("device", device);
	            data.put("socket", socket);
            	msg.obj = data;
            	handler.sendMessage(msg);
	    	}
	     public void cancel() {
	            try {
	                socket.close();
	            } catch (IOException e) {
	            }
	        }
	    }
	 
	 /*������ģ�����ϵķ��������̣����������豸���͹�������������*/
	 private class EmulatorServerThread extends Thread{
		ServerSocket emulatorSocket;
		
		public EmulatorServerThread(){
			try{
				emulatorSocket = new ServerSocket(listenPort);
			}
			catch(IOException e){
				Log.i("FileShare_ConnectionManager","can not get emulatorSocket");
			}
		}
		
		public void run(){
			Log.i("FileShare_ConnectionManager","emulator server run");
			while(true){
				Socket socket = null;
				try {                   
					socket = emulatorSocket.accept();
	            } catch (IOException e) {
	                Log.i("FileShare_ConnectionManager_EmulatorServerThread", "accept fail");
	                break;
	            }
	            /*���ӳɹ����������淢�͡����ӳɹ���,��������Ӧ��Socket*/
	            if (socket != null) {
	            	Log.i("fileshare","before read");
	            	byte[] buffer = new byte[20];
	            	int bytes = 0;
	            	try{
	            		bytes = socket.getInputStream().read(buffer);
	            	}
	            	catch(IOException e){}
	            	
	            	int port = Integer.valueOf(new String(buffer,0,bytes)).intValue();
	            	Log.i("fileshare","get port in server:"+port);
	                Message msg = handler.obtainMessage(FileShare.MESSAGE_CONNECTION_EMULATOR_SUCCESS);	    
	                msg.obj = socket;
	                msg.arg1 = port;
	                handler.sendMessage(msg);
	            }
			}
		}
		
		public void cancel() {
	           try {
	        	   emulatorSocket.close();
	            } catch (IOException e) {
	                Log.i("FileShare_ConnectionManager", "can not close emulatorSocket");
	            }
	        }
	 }
	 /*������ģ�����ϵ������豸�Ŀͻ��˽���*/
	 private class EmulatorClientThread extends Thread{
		 int serverPort;
		 
		 public EmulatorClientThread(int port){
			 serverPort = port;
		 }
		 
		 public void run(){
			 Socket socket = null;
			 try{
				 socket = new Socket(SERVERLOCALIP,serverPort);
				 /*��Է����ʹ�ģ���������Ķ˿�*/
				 socket.getOutputStream().write(new Integer(listenPort).toString().getBytes());
				 /*�������淢�͡����ӳɹ�������������Ӧ��Socket*/				 
		         Message msg = handler.obtainMessage(FileShare.MESSAGE_CONNECTION_EMULATOR_SUCCESS);		         
	             msg.obj = socket;
	             msg.arg1 = serverPort;
	             handler.sendMessage(msg);
	             
			 }
			 catch(IOException e){
				 /*�������淢�͡�����ʧ�ܡ�*/
	            Message msg = handler.obtainMessage(FileShare.MESSAGE_CONNECTION_EMULATOR_FAIL);
	            msg.arg1 = serverPort;
	            handler.sendMessage(msg);
	            if(socket != null){
	            	try {
		                  socket.close();
		            } catch (IOException e2) {                  
		            }
	            }
	            
			 }			 
		 }
	 }
}
