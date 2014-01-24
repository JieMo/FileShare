package mine.fileshare;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;


/**
 * CommunicationManager���������豸֮������ݴ���
 *
 */
public class CommunicationManager {
	private ArrayList<FSFile> myFiles;
	private ArrayList<FSFile> othersFiles;
	private Handler handler;
	private ArrayList<Device> devices;
	
	private HashMap<String,Socket> communicateSocket;//���豸�ı�ʶӳ�䵽��Ӧ��socket��
	private HashMap<Socket,ReadThread> allReadThread;//ÿһ��socket���Ӷ�Ӧ�Ķ�ȡ���ݵ��߳�
	private ArrayList<GetFileRequest> allRequests;//�������豸�����л�ȡ�ļ�����
	private ArrayList<String> reqToID;//�ظ�ĳ������ȡ�ļ�����ʱ���˻ظ�Ӧ��ת�������豸��id
	private ArrayList<ArrayList<String>> otherNotReq;
	private ArrayList<GetFileRequest> myGetFileReq;//���豸���͵����л�ȡ�ļ�����
	private ArrayList<ArrayList<String>> myNotReq;
	private HashMap<String,Integer> deviceRecentReq;//ÿһ���豸�����һ�λ�ȡ�ļ���������
	
	private ArrayList<FileSearchRequest> fileSearchReq;//�������豸�����в����ļ�����
	private ArrayList<String> fileSearchReqToID;//�ظ�ĳ���������ļ�����ʱ���˻ظ�Ӧ��ת�������豸��id
	private ArrayList<ArrayList<FSFile>> fileSearchResult;//ĳ���������ļ����󡱶�Ӧ�Ľ��
	private ArrayList<ArrayList<String>> notResponse;//ĳ���������ļ����󡱶�Ӧ�����л�δ�ظ����豸
	private HashMap<String,Integer> recentSearchReq;//ÿһ���豸�����һ�β����ļ���������
	//private int myNotResponse;
	//private ArrayList<FSFile> mySearchResult;
	
	private ServerSocket fileTransferServer = null;
	
	public CommunicationManager(Context context, 
			                    Handler handler,
			                    ArrayList<FSFile> myFiles,
			                    ArrayList<FSFile> othersFiles,
			                    ArrayList<Device> devices){
		this.handler = handler;
		this.myFiles = myFiles;
		this.othersFiles = othersFiles;
		this.devices = devices;
		this.communicateSocket = new HashMap<String,Socket>();
		this.allReadThread = new HashMap<Socket,ReadThread>();
		this.allRequests = new ArrayList<GetFileRequest>();
		this.reqToID = new ArrayList<String>();
		this.otherNotReq = new ArrayList<ArrayList<String>>();
		this.myGetFileReq = new ArrayList<GetFileRequest>();
		this.myNotReq = new ArrayList<ArrayList<String>>();
		this.deviceRecentReq = new HashMap<String,Integer>();
		this.fileSearchReq = new ArrayList<FileSearchRequest>();
		this.fileSearchReqToID = new ArrayList<String>();
		this.fileSearchResult = new ArrayList<ArrayList<FSFile>>();
		this.notResponse = new ArrayList<ArrayList<String>>();
		this.recentSearchReq = new HashMap<String,Integer>();
		//this.mySearchResult = new ArrayList<FSFile>();
	}
	
	public void start(int listenPort){
		try{
			fileTransferServer = new ServerSocket(listenPort+1);
		}
		catch(IOException e){
			Log.e("fileshare","can not get ServerSocket for file transfer",e);
		}
	}
	
	/*
	 * ����µ�socket
	 */
	public void newSocket(String deviceID,Socket socket){
		communicateSocket.put(deviceID,socket);
		ReadThread readThread = new ReadThread(socket,deviceID);
		allReadThread.put(socket, readThread);
		readThread.start();
	}
	
	/*
	 * ��ָ���豸������Ϣ
	 */
	public void sendMessage(String deviceID,FSMessage message){
		Log.i("fileshare",""+message.flag+" to "+deviceID);
		if(message.flag == FSMessage.DEVICE_DEPARTURE){
			try{
				communicateSocket.get(deviceID).getOutputStream().write(serialize(message));
			}catch(IOException e){
				Log.e("fileshare","cannot send departure message",e);
			}
			
		}
		else{
			WriteThread sendMsg = new WriteThread(communicateSocket.get(deviceID),serialize(message));
			sendMsg.start();
		}
		
	}
	
	/*
	 * �����˳��������д򿪵�socket�ر�
	 */
	public void stop(){
		for(Socket socket : communicateSocket.values()){
			try {
				allReadThread.get(socket).cancel();
				//allReadThread.get(socket).interrupt();
				//allReadThread.get(socket).stop();
				//socket.shutdownOutput();			
                socket.close();
            } catch (IOException e) {
                Log.e("fileshare", "cannot close socket", e);
            }
		}
		try{
			fileTransferServer.close();
		}catch(IOException e){
			Log.e("fileshare","cannot close ServerSocket for file transfer");
		}
	}
	
	public void interruptReadThread(){
		for(Socket socket : communicateSocket.values()){
			//allReadThread.get(socket).cancel();
			allReadThread.get(socket).interruptThread();
		}
	}
	
	/*
	 * �ر���ָ���豸֮���socket
	 */
	public synchronized void closeSocket(String deviceID){
		try {
			allReadThread.get(communicateSocket.get(deviceID)).cancel();
			allReadThread.get(communicateSocket.get(deviceID)).interruptThread();
			//communicateSocket.get(deviceID).shutdownOutput();			
			communicateSocket.get(deviceID).close();
			communicateSocket.remove(deviceID);
			if(deviceRecentReq.containsKey(deviceID))
				deviceRecentReq.remove(deviceID);
        } catch (IOException e) {
            Log.e("fileshare", "cannot close socket", e);
        }
	}
	
	public void initNotRequested(GetFileRequest request,ArrayList<String> devices){
		synchronized(this){
			myGetFileReq.add(request);
			myNotReq.add(devices);
		}
	}
	
	
	/*
	 * ��FSMessage���л��Ա㴫��
	 */
	private byte[] serialize(FSMessage message){
		byte[] result = new byte[1];
		try{
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(message);
			result = baos.toByteArray();
		}catch(Exception e){
			Log.e("fileshare","cannot serialize message",e);
		}
		
		return result;
	}
	
	/*
	 * ����ȡ���ֽ��������л���FSMessage
	 */
	private FSMessage getMessage(byte[] data){
		FSMessage message = null;
		try{	
			ByteArrayInputStream bais = new ByteArrayInputStream(data);
			ObjectInputStream ois = new ObjectInputStream(bais);
			message = (FSMessage)ois.readObject();
		}catch(Exception e){
			Log.e("fileshare","cannot unserialize message",e);
		}
		
		return message;
	}
	
	/*
	 * ���ݲ�ͬ����Ϣ������Ӧ�Ļ�Ӧ����Ϣ���Ϳ��ɱ�־λȷ��
	 */
	private void response(FSMessage message,String deviceID){
		if(message != null){
			switch(message.flag){
			case FSMessage.ADD_FILES:
				Message msg = handler.obtainMessage(FileShare.MESSAGE_ADD_FILES);
				ArrayList<FSFile> fileList = (ArrayList<FSFile>)message.data;
				HashMap<String,Object> data = new HashMap<String,Object>();
//				data.put("deviceID", deviceID);
//				data.put("filelist", message.data);
				for(FSFile eachFile : fileList)
					eachFile.transitDevice = deviceID;
				msg.obj = fileList;
				handler.sendMessage(msg);
				break;
			case FSMessage.GET_FILE:
				GetFileRequest request = (GetFileRequest)message.data;
				FSFile requestFile = request.file;
				
				synchronized(this){
					if(deviceRecentReq.containsKey(request.sourceID)){
						if(request.requestNum <= deviceRecentReq.get(request.sourceID)){
							Log.i("fileshare","currentNum:"+deviceRecentReq.get(request.sourceID)+" request:"+request.requestNum);
							FSMessage fileNotExist = new FSMessage(FSMessage.FILE_NOT_EXIST,request);
							sendMessage(deviceID,fileNotExist);
							return;
						}
						else{
							deviceRecentReq.remove(request.sourceID);
						}
					}
					
					deviceRecentReq.put(request.sourceID, request.requestNum);
				}
											
				/*���������ļ��ڱ�����ֱ�Ӵ����ļ�*/
				if(myFiles.contains(requestFile)){
					FSMessage transferMsg = new FSMessage(FSMessage.TRANSFER_FILE_READY,request);
					sendMessage(deviceID,transferMsg);
					
					msg = handler.obtainMessage(FileShare.MESSAGE_GET_FILE_HIT);
					data = new HashMap<String,Object>();
					data.put("deviceID", deviceID);
					data.put("file",requestFile);
					msg.obj = data;
					handler.sendMessage(msg);
					
					/*�����ļ�*/
					sendFile(deviceID,requestFile.fileName);					
					
				}				
				else if(othersFiles.contains(requestFile)){
					FSFile file = othersFiles.get(othersFiles.indexOf(requestFile));
					/*����ļ��ڱ����и�����ֱ�Ӵ���*/
					if(file.gotten || file.cached){
						FSMessage transferMsg = new FSMessage(FSMessage.TRANSFER_FILE_READY,request);
						sendMessage(deviceID,transferMsg);
						
						msg = handler.obtainMessage(FileShare.MESSAGE_GET_FILE_HIT);
						data = new HashMap<String,Object>();
						data.put("deviceID", deviceID);
						data.put("file",requestFile);
						msg.obj = data;
						handler.sendMessage(msg);
						
						String fileName = null;
						if(file.gotten)
							fileName = FileShare.APPDIR + "/" + getFileName(file);
						else if(file.cached)
							fileName = FileShare.CACHEDIR + "/" + getFileName(file);
						sendFile(deviceID,fileName);
					}
					/*����ת������*/
					else{
						ArrayList<String> notReq = new ArrayList<String>();
						for(Device dev : devices){
							String devID = "emulator-"+dev.port;
							if(communicateSocket.containsKey(devID) && !devID.equals(file.transitDevice) && !deviceID.equals(devID))
								notReq.add(devID);
						}
						
						synchronized(this){
							allRequests.add(request);
							reqToID.add(deviceID);	
							otherNotReq.add(notReq);
						}
																					
						sendMessage(file.transitDevice,message);
						
						msg = handler.obtainMessage(FileShare.MESSAGE_GET_FILE_NOTHIT);
						data = new HashMap<String,Object>();
						data.put("deviceID", deviceID);
						data.put("transitDevice", file.transitDevice);
						data.put("file",requestFile);
						msg.obj = data;
						handler.sendMessage(msg);
					}								
				}
				/*��֪�Է��ļ�������*/
				else{
					FSMessage fileNotExist = new FSMessage(FSMessage.FILE_NOT_EXIST,request);
					sendMessage(deviceID,fileNotExist);
				}
			
				break;
			case FSMessage.TRANSFER_FILE_READY:
				request = (GetFileRequest)message.data;
				requestFile = request.file;
				if(request.sourceID.equals(FileShare.thisID)){
					msg = handler.obtainMessage(FileShare.MESSAGE_GET_FILE_MINE);
					data = new HashMap<String,Object>();
					data.put("deviceID", deviceID);
					data.put("file",requestFile);
					msg.obj = data;
					handler.sendMessage(msg);
					
					/*������������ļ�*/
					String fileName = getFileName(requestFile);
					storeFile(deviceID,fileName,false);
					
				}
				else{
					String device = reqToID.get(allRequests.indexOf(request));								
					
					/*���ļ����뻺���ļ���*/
					String fileName = getFileName(requestFile);
					storeFile(deviceID,fileName,true);
					
					msg = handler.obtainMessage(FileShare.MESSAGE_GET_FILE_OTHERS);
					data = new HashMap<String,Object>();
					data.put("deviceID", deviceID);
					data.put("transitDevice", device);
					data.put("file",requestFile);
					msg.obj = data;
					handler.sendMessage(msg);
					
					/*�����ļ�����һ���豸�ڵ�*/
					sendMessage(device,message);
					sendFile(device,FileShare.CACHEDIR+"/"+fileName);
					
					synchronized(this){
						reqToID.remove(allRequests.indexOf(request));
						allRequests.remove(request);	
					}								
				}
				break;
			case FSMessage.FILE_NOT_EXIST:
				request = (GetFileRequest)message.data;
				ArrayList<String> notRequested = request.sourceID.equals(FileShare.thisID) ?
						                          myNotReq.get(myGetFileReq.indexOf(request)) :
						                          otherNotReq.get(allRequests.indexOf(request));
				if(notRequested.size() > 0){
					String device = notRequested.remove(0);
					sendMessage(device,new FSMessage(FSMessage.GET_FILE,request));
				}
				else{
					msg = handler.obtainMessage(FileShare.MESSAGE_FILE_NOTEXIST);
					msg.obj = request;
					handler.sendMessage(msg);
					
					if(!request.sourceID.equals(FileShare.thisID)){
						sendMessage(reqToID.get(allRequests.indexOf(request)),message);
						
						synchronized(this){
							reqToID.remove(allRequests.indexOf(request));
							otherNotReq.remove(allRequests.indexOf(request));
							allRequests.remove(request);	
						}	
					}
					else{
						synchronized(this){
							myNotReq.remove(myGetFileReq.indexOf(request));
							myGetFileReq.remove(request);
						}
					}
				}
				break;
			case FSMessage.SEARCH_FILE:
				Log.i("fileshare","get file search request from "+deviceID);
				FileSearchRequest searchReq = (FileSearchRequest)message.data;
				String tag = searchReq.tag;
				
				synchronized(this){
					if(recentSearchReq.containsKey(searchReq.sourceID)){
						if(searchReq.requestNum <= recentSearchReq.get(searchReq.sourceID)){
							Log.i("fileshare","currentNum:"+recentSearchReq.get(searchReq.sourceID)+" request:"+searchReq.requestNum);
							HashMap<String,Object> result = new HashMap<String,Object>();
							result.put("request", searchReq);
							result.put("result", new ArrayList<FSFile>());
							FSMessage response = new FSMessage(FSMessage.SEARCH_RESULT,result);
							sendMessage(deviceID,response);
							return;
						}
						else{
							recentSearchReq.remove(searchReq.sourceID);
						}
					}
					
					recentSearchReq.put(searchReq.sourceID, searchReq.requestNum);
				}
				
				ArrayList<FSFile> localSearchResult = lookUp(tag);
				
				if(localSearchResult.size() > 0){
					HashMap<String,Object> result = new HashMap<String,Object>();
					result.put("request", searchReq);
					result.put("result", localSearchResult);
					sendMessage(deviceID,new FSMessage(FSMessage.SEARCH_RESULT,result));
				}
				else{
					synchronized(this){
						int notRequest = 0;
						ArrayList<String> notReq = new ArrayList<String>();
						for(Device d : devices)
							if(d.connection_state && !deviceID.equals("emulator-"+d.port) && !searchReq.sourceID.equals("emulator-"+d.port)){
								notRequest++;
								notReq.add("emulator-"+d.port);
								sendMessage("emulator-"+d.port,message);
							}
						if(notRequest == 0){
							HashMap<String,Object> result = new HashMap<String,Object>();
							result.put("request", searchReq);
							result.put("result", localSearchResult);
							sendMessage(deviceID,new FSMessage(FSMessage.SEARCH_RESULT,result));
						}
						else{
							fileSearchReq.add(searchReq);
							notResponse.add(notReq);
							fileSearchResult.add(new ArrayList<FSFile>());
							fileSearchReqToID.add(deviceID);
						}
					}
					
				}
				break;
			case FSMessage.SEARCH_RESULT:				
				HashMap<String,Object> result = (HashMap<String,Object>)message.data;
				searchReq = (FileSearchRequest)result.get("request");
				ArrayList<FSFile> matchedFile = (ArrayList<FSFile>)result.get("result");
				Log.i("fileshare","get search result from "+deviceID+" to "+searchReq.sourceID);
				
				synchronized(this){
					if(searchReq.sourceID.equals(FileShare.thisID)){
//						for(FSFile file : matchedFile)
//							if(!mySearchResult.contains(file))
//								mySearchResult.add(file);
//						myNotResponse--;
//						if(myNotResponse == 0){
//							msg = handler.obtainMessage(FileShare.MESSAGE_SEARCH_RESULT);
//							msg.obj = mySearchResult;
//							handler.sendMessage(msg);
//						}
						msg = handler.obtainMessage(FileShare.MESSAGE_SEARCH_RESULT);
						msg.arg1 = getPort(deviceID);
						msg.obj = matchedFile;
						handler.sendMessage(msg);
					}
					else{
						int index = fileSearchReq.indexOf(searchReq);
						for(FSFile file : matchedFile){
							Log.i("fileshare","hop:"+file.hop);
							file.hop++;
							file.transitDevice = deviceID;
							fileSearchResult.get(index).add(file);
						}
//						int notRes = notResponse.get(index);
//						notRes--;
//						notResponse.set(index, notRes);
						notResponse.get(index).remove(deviceID);
						if(notResponse.get(index).size() == 0){
							HashMap<String,Object> r = new HashMap<String,Object>();
							r.put("request", searchReq);
							r.put("result", fileSearchResult.get(index));
							sendMessage(fileSearchReqToID.get(index),new FSMessage(FSMessage.SEARCH_RESULT,result));
							
							for(FSFile eachF : fileSearchResult.get(index))
								eachF.hop--;
							msg = handler.obtainMessage(FileShare.MESSAGE_ADD_FILES);
							msg.obj = fileSearchResult.get(index);
							handler.sendMessage(msg);
							
							fileSearchResult.remove(index);
							notResponse.remove(index);
							fileSearchReqToID.remove(index);
							fileSearchReq.remove(index);
						}
					}
				}
				break;
			case FSMessage.UPDATE_TAG:
				msg = handler.obtainMessage(FileShare.MESSAGE_UPDATE_TAG);
				msg.obj = message.data;
				handler.sendMessage(msg);
				break;
			case FSMessage.DEVICE_DEPARTURE:
				msg = handler.obtainMessage(FileShare.MESSAGE_DEVICE_DEPARTURE);
				msg.obj = deviceID;
				handler.sendMessage(msg);
				break;
			}
		}	
	}
	
	/*
	 * ���߳�������socket�ж�ȡ����
	 */
	private class ReadThread extends Thread{
		Socket socket;
		InputStream inputStream;
		String deviceID;
		
		private boolean disconnected = false;
		private Thread thisThread = null;
		
		public ReadThread(Socket socket,String ID){
			this.socket = socket;
			this.deviceID = ID;
			try{
				this.inputStream = socket.getInputStream();
			}
			catch(IOException e){
				Log.e("fileshare","cannot get inputStream from socket between "+deviceID);
			}
			
		}
		
		public void run() {
			thisThread = Thread.currentThread();
			
            byte[] buffer = new byte[1024];
            int bytes;

            while (!disconnected) {
                try {
                    bytes = inputStream.read(buffer);                  
                } catch (IOException e) {
                    Log.e("fileshare", "disconnected to "+deviceID);
                    //connectionLost();
                    break;
                }
                if(!disconnected)
                	response(getMessage(buffer),deviceID);
            }
            Log.i("fileshare","read thread interrupted");
        }
		
		public void cancel(){
			try{
				inputStream.close();
			}
			catch(IOException e){
				Log.e("fileshare","cannot close inputStream of socket between "+deviceID);
			}
		}
		
		public void interruptThread(){
			disconnected = true;
			if(thisThread != null)
				thisThread.interrupt();
		}
	}
	
	/*
	 * ���߳�������socketд����
	 */
	private class WriteThread extends Thread{
		Socket socket;
		OutputStream outputStream;
		byte[] data;
		
		public WriteThread(Socket socket,byte[] data){
			this.socket = socket;
			this.data = data;
			try{
				this.outputStream = socket.getOutputStream();
			}
			catch(IOException e){
				Log.e("fileshare","cannot get outputStream");
			}
			
		}
		
		public void run() {
			Log.i("fileshare","start write");

			try {
				if(outputStream != null){
                outputStream.write(data);
                outputStream.flush();
				}
             } catch (IOException e) {
                 Log.e("fileshare", "writethread cannot write");
                 //connectionLost();
            }             
        }
	}
	
	private String getFileName(FSFile file){
		String[] fileNameSplit = file.fileName.split("/");
		return file.actualOwner+"-"+fileNameSplit[fileNameSplit.length-1];
	}
	
	/*����ָ�����ļ�*/
	private void sendFile(String deviceID,String fileName){
		File theFile = new File(fileName);
		FileInputStream fis = null;
		try{
			if(theFile.exists()){
				byte[] fileContent = new byte[1024];
				fis = new FileInputStream(theFile);

				/*����һ���µ�Socket���ڴ����ļ�*/				
				Socket sendFileSocket = null;
				OutputStream os = null;
				try {                   
					sendFileSocket = fileTransferServer.accept();
					Log.i("fileshare","accept succeed in sendFile");					
	            } catch (IOException e) {
	                Log.e("fileshare", "accept fail in sendFile() to "+deviceID,e);
	                return;
	            }
	            
	            if(sendFileSocket != null){
	            	Log.i("fileshare","start transfering file:"+fileName);
	            	os = sendFileSocket.getOutputStream();
	            	int bytes = 0;
	            	while(true){
	            		try {
		            		if((bytes = fis.read(fileContent)) > 0){
		            			if(bytes == 1024)
		            				os.write(fileContent);
		            			else
		            				os.write(fileContent, 0, bytes);
		            		}
		            		else
		            			break;
		                 } catch (IOException e) {
		                     Log.e("fileshare", "cannot send file:"+fileName+" to "+deviceID);
		                }
	            	}
	            	
	                if(os != null){
	                	try{
	                		os.flush();
	                		os.close();
	                	}catch(IOException e){
	                		Log.e("fileshare","cannot close outputStream in sendFile() to "+deviceID);
	                	}
	                }
	                try{
	                	sendFileSocket.close();
	                }catch(IOException e){
	                	Log.e("fileshare","cannot close socket in sendFile() to "+deviceID);
	                }
	            }
			}
		}catch(IOException e){
			Log.e("fileshare","cannot transfer file:"+fileName,e);
		}finally{
			try{
				if(fis != null)
					fis.close();
			}catch(IOException e){
				Log.e("fileshare","file close error:"+fileName,e);
			}
		}
	}
	
	/*��������ļ��洢������*/
	private void storeFile(String deviceID,String fileName,boolean cache){
		fileName = cache ? FileShare.CACHEDIR+"/"+fileName : FileShare.APPDIR + "/" + fileName;
		File file = new File(fileName);
		FileOutputStream fos = null;
		byte[] fileContent = new byte[1024];
		Socket getFileSocket = null;
		InputStream is = null;
		
		try{
			if(!file.exists())
				file.createNewFile();
			if(file.exists() && file.canWrite()){
				fos = new FileOutputStream(file);
			}
		}catch(IOException e){
			Log.e("fileshare","cannot create file while getting file:"+fileName);
			return;
		}
		
		
		try{
			getFileSocket = new Socket(ConnectionManager.SERVERLOCALIP,getPort(deviceID)+1);
		}catch(IOException e){
			Log.e("fileshare","cannot connect to "+deviceID+" in storeFile()",e);
			return;
		}
		
		try{
			is = getFileSocket.getInputStream();
			int bytes = 0;
			while((bytes = is.read(fileContent)) > 0){
				if(bytes == 1024)
					fos.write(fileContent);
				else
					fos.write(fileContent, 0, bytes);
			}			
			
			Log.i("fileshare","finishing read file:"+fileName);
					
			if(!cache){
				Message msg = handler.obtainMessage(FileShare.MESSAGE_FILE_STORED);
				msg.obj = fileName;
				handler.sendMessage(msg);
			}
		}catch(IOException e){
			Log.e("fileshare","error occures when getting file:"+fileName,e);
		}finally{
			if(fos != null){
				try{
					fos.flush();
					fos.close();
				}catch(IOException e){
					Log.e("fileshare","close fileOutputStream error",e);
				}
				
				try{
					is.close();
					getFileSocket.close();
				}catch(IOException e){
					Log.e("fileshare","cannot close getFileSocket when getting file:"+fileName+" from "+deviceID,e);
				}
			}
		}
	}
	
	private int getPort(String deviceID){
		return Integer.valueOf(deviceID.substring(deviceID.length()-4, deviceID.length()));
	}
	
	/*�ڱ��ز����Ƿ��з��ϸ�����ǩ���ļ�*/
	private ArrayList<FSFile> lookUp(String tag){
		ArrayList<FSFile> result = new ArrayList<FSFile>();
		
		for(FSFile file : myFiles){
			if(match(file,tag))
				result.add(file);
		}
		for(FSFile file : othersFiles){
			if(match(file,tag))
				result.add(file);
		}
		
		return result;
	}
	
	private boolean match(FSFile file,String tag){
		for(String tagHave : file.tags)
			if(tagHave.contains(tag) || tag.contains(tagHave))
				return true;
		
		return false;
	}

}
