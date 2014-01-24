package mine.fileshare;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.TabActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.SimpleAdapter;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * FileShare��Ҫ�����UI��أ�ͬʱ�Ǹ��ֹ��ܵ���ڣ���������豸����ӹ����ļ��������ļ��ȡ�
 * ���ݲ�ͬ���ܵ���ConnectionManager��CommunicationManager�е���غ�����
 * 
 * @author ����
 */
public class FileShare extends TabActivity {
	/*�����б�Ŀ�ݲ˵��в˵����id*/
	private final int DEVICE_LIST_CONTEXT_MENU_CONNECT = 1,
	                  MYFILES_LIST_CONTEXT_MENU_ADDTAG = 2,
	                  OTHERSFILES_LIST_CONTEXT_MENU_INFO = 3,
	                  OTHERSFILES_LIST_CONTEXT_MENU_GET = 4,
	                  DEVICE_LIST_CONTEXT_MENU_DISCONNECT = 5,
	                  MYFILES_LIST_CONTEXT_MENU_PREVIEW = 6,
	                  OTHERSFILES_LIST_CONTEXT_MENU_PREVIEW = 7;
	
	/*startActivityForResult�����е�����id*/
	private final int REQUEST_ENABLE_BLUETOOTH = 1,
					  REQUEST_ADD_FILES = 2;
	
	/*UI��ǩ��id*/
	private final int TAB_DEVICE = 0,
	                  TAB_MYFILE = 1,
	                  TAB_OTHERFILE = 2;
	
	/*ConnectionManager��CommunicationManager�е��߳���UI���߳�ͨ����Ҫ���ݵĸ�����Ϣ����*/
	public static final int MESSAGE_CONNECTION_FAIL = 1,
	                        MESSAGE_CONNECTION_SUCCESS = 2,
	                        MESSAGE_CONNECTION_EMULATOR_SUCCESS = 3,
	                        MESSAGE_CONNECTION_EMULATOR_FAIL = 4,
	                        MESSAGE_ADD_FILES = 5,
	                        MESSAGE_DEVICE_DEPARTURE = 6,
	                        MESSAGE_GET_FILE_HIT = 7,
	                        MESSAGE_GET_FILE_NOTHIT = 8,
	                        MESSAGE_GET_FILE_MINE = 9,
	                        MESSAGE_GET_FILE_OTHERS = 10,
	                        MESSAGE_FILE_STORED = 11,
	                        MESSAGE_FILE_NOTEXIST = 12,
	                        MESSAGE_SEARCH_RESULT = 13,
	                        MESSAGE_UPDATE_TAG = 14;	                        ;
	
	public static final String APPDIR = "/sdcard/file_share";
	public static final String CACHEDIR = "/sdcard/file_share/cache";
	
	private FSFile addTagFile;
	
	private TabHost tabs;
	//�Ƿ�ͨ��ģ��������
	public static final boolean ISEMULATOR = true;
	private int listenPort;
	private AlertDialog.Builder inputDialog;
	private EditText inputText;
	private TextView textView;
	//�豸�б���ͼ
	private ListView device_list;
	//���ҵĹ����ļ����б���ͼ
	private ListView my_file_list;
	//�����˵Ĺ����ļ����б���ͼ
	private ListView others_file_list;
	//�����ӵ��豸
	private List<Map<String,Object>> devicesList;
	private ArrayList<Device> devices;
	//�ҹ�����ļ�
	private List<Map<String,Object>> myFilesList;
	private ArrayList<FSFile> myFiles;
	//���˹�����ļ�
	private List<Map<String,Object>> othersFilesList;
	private ArrayList<FSFile> othersFiles;
	
	//���ص�����������
	private BluetoothAdapter bluetoothAdapter = null;
	//ɨ���豸ʱ��ʾ�Ľ������Ի���
	private ProgressDialog scanProgress;
	
	//��������ģ��
	private ConnectionManager connectionMgr = null;
	public static String thisID;
	
	//����ͨ��ģ��
	private CommunicationManager communicationMgr = null;
	private int getFileRequestNum = 0;
	private int fileSearchRequestNum = 0;
	
	//�ļ�����ʱ��δ�ظ����豸��ID
	private ArrayList<String> notResponse = new ArrayList<String>();
	//�ļ������Ľ��
	private ArrayList<FSFile> searchResult = new ArrayList<FSFile>();
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.main);
        
        /*�ж�SD���Ƿ���ڣ�����SD������ֱ���˳�*/
        if(!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
        	Toast.makeText(this, "���Ȳ���SD���ٿ�������", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        /*�жϳ���洢�ļ���Ŀ¼�Ƿ��Ѿ����ڣ����޴���Ŀ¼*/
        File appDir = new File(APPDIR);
        File cacheDir = new File(CACHEDIR);
        if(!appDir.exists())
        	if(!appDir.mkdir()){
        		Toast.makeText(this, "���ܴ�������Ŀ¼", Toast.LENGTH_LONG).show();
                finish();
                return;
        	}
        if(!cacheDir.exists())
        	if(!cacheDir.mkdir()){
        		Toast.makeText(this, "���ܴ�������Ŀ¼", Toast.LENGTH_LONG).show();
                finish();
                return;
        	}
        
        /*��ʾ�����豸�Ķ˿ںš�������������ģ������ʱ����Ҫȷ����Ϊ���������������Ķ˿ںţ��˶˿ں�Ҳ��Ϊģ�����ı�ʶ*/
        inputDialog = new AlertDialog.Builder(this);
        inputDialog.setTitle("����˿ں�");
        inputText = new EditText(this);
        inputDialog.setView(inputText);
        inputDialog.setPositiveButton("ȷ��", new DialogInterface.OnClickListener(){
        	public void onClick(DialogInterface d,int arg){
        		listenPort = Integer.valueOf(inputText.getText().toString());
        		thisID = "emulator-"+listenPort;
        		connectionMgr = new ConnectionManager(FileShare.this,handler,listenPort);
            	connectionMgr.start();
            	
                communicationMgr = new CommunicationManager(FileShare.this,handler,myFiles,othersFiles,devices);
                communicationMgr.start(listenPort);
                
        	}
        });
        inputDialog.show();
        
        tabs = this.getTabHost();
        LayoutInflater.from(this).inflate(R.layout.main, tabs.getTabContentView());
        
        TabHost.TabSpec spec = tabs.newTabSpec("tag1");
        spec.setContent(R.id.device_list);
        spec.setIndicator("�����豸");
        tabs.addTab(spec);
        
        spec = tabs.newTabSpec("tag2");
        spec.setContent(R.id.my_file_list);
        spec.setIndicator("�ҵ��ļ�");
        tabs.addTab(spec);
        
        spec = tabs.newTabSpec("tag3");
        spec.setContent(R.id.others_file_list);
        spec.setIndicator("�����ļ�");
        tabs.addTab(spec);
        
        tabs.setCurrentTab(0);
                
        device_list = (ListView)findViewById(R.id.device_list);
        my_file_list = (ListView)findViewById(R.id.my_file_list);
        others_file_list = (ListView)findViewById(R.id.others_file_list);
        
        devicesList = new ArrayList<Map<String,Object>>();
        devices = new ArrayList<Device>();
       
        /*�����豸�б������������*/
        SimpleAdapter adapter = new SimpleAdapter(this,devicesList,R.layout.device,
        		                                  new String[]{"device_name","connection_state"},
        		                                  new int[]{R.id.device_name,R.id.connection_state});
        device_list.setAdapter(adapter);
        /*�����豸�б�ĳ����¼�������*/
        device_list.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener(){
        	    @Override
    		    public void onCreateContextMenu(ContextMenu menu, View v,
    		                                    ContextMenuInfo menuInfo) {
    			menu.add(Menu.NONE,DEVICE_LIST_CONTEXT_MENU_CONNECT,0,"����");
    			menu.add(Menu.NONE,DEVICE_LIST_CONTEXT_MENU_DISCONNECT,1,"�Ͽ�");
    		}
        });
        
        /*����"�ҵĹ����ļ�"�б������������*/
        myFilesList = new ArrayList<Map<String,Object>>();
        myFiles = new ArrayList<FSFile>();
        
        adapter = new SimpleAdapter(this,myFilesList,R.layout.filelist,
                                    new String[]{"fileName"},
                                    new int[]{R.id.file_name});
        my_file_list.setAdapter(adapter);
        /*����"�ҵĹ����ļ�"�б�ĳ����¼�������*/
        my_file_list.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener(){
        	    @Override
    		    public void onCreateContextMenu(ContextMenu menu, View v,
    		                                    ContextMenuInfo menuInfo) {
    			menu.add(Menu.NONE,MYFILES_LIST_CONTEXT_MENU_ADDTAG,0,"��ӱ�ǩ");
    			menu.add(Menu.NONE,MYFILES_LIST_CONTEXT_MENU_PREVIEW,1,"�鿴");
    		}
        });
        
        /*����"���˵Ĺ����ļ�"�б������������*/
        othersFilesList = new ArrayList<Map<String,Object>>();
        othersFiles = new ArrayList<FSFile>();
        
        adapter = new SimpleAdapter(this,othersFilesList,R.layout.filelist,
                                    new String[]{"fileName"},
                                    new int[]{R.id.file_name});
        others_file_list.setAdapter(adapter);
        /*����"���˵Ĺ����ļ�"�б�ĳ����¼�������*/
        others_file_list.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener(){
        	    @Override
    		    public void onCreateContextMenu(ContextMenu menu, View v,
    		                                    ContextMenuInfo menuInfo) {
    			menu.add(Menu.NONE,OTHERSFILES_LIST_CONTEXT_MENU_INFO,0,"��ϸ��Ϣ");
    			menu.add(Menu.NONE,OTHERSFILES_LIST_CONTEXT_MENU_GET,1,"��ȡ");
    			menu.add(Menu.NONE,OTHERSFILES_LIST_CONTEXT_MENU_PREVIEW,2,"�鿴");
    		}
        });

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
              
//        /*���bluetoothAdapterΪnull��˵���豸��֧������������ֱ���˳�*/
//        if (bluetoothAdapter == null && !ISEMULATOR) {
//            Toast.makeText(this, "�ܱ�Ǹ�����豸��֧����������", Toast.LENGTH_LONG).show();
//            finish();
//            return;
//        }
    }
    
    @Override
    public void onStart() {
        super.onStart();
  
        if(!ISEMULATOR){
        	/*ע��㲥������scanReceiver*/
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(scanReceiver, filter);

            filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            registerReceiver(scanReceiver, filter);
            
            /*������δ��������������*/
            if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
            } 
        }       
//        if(communicationMgr == null){
//        	communicationMgr = new CommunicationManager(this,handler,myFiles,othersFiles);
//        	communicationMgr.start();
//        }
        
//        if(connectionMgr == null){
//        	connectionMgr = new ConnectionManager(this,handler,listenPort);
//        	connectionMgr.start();
//        }
    }
    
    @Override
    public synchronized void onResume() {
        super.onResume();
       // communicationMgr.start();
    }
    
    @Override
    public void onStop() {
        super.onStop();
        if(!ISEMULATOR)
        	this.unregisterReceiver(scanReceiver);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        /*�����˳�ʱ֪ͨ�����豸*/
        if(bluetoothAdapter != null)
        	bluetoothAdapter.cancelDiscovery();
        if(connectionMgr != null)
        	connectionMgr.stop();
        if(communicationMgr != null){
        	communicationMgr.interruptReadThread();
        	for(Device d : devices){
        		if(d.connection_state){
        			FSMessage message = new FSMessage(FSMessage.DEVICE_DEPARTURE,null);
        			communicationMgr.sendMessage("emulator-"+d.port, message);
        		}
        	}
        	communicationMgr.stop();
        }
        /*ɾ�����л�����ļ�*/
        File cacheDir = new File(CACHEDIR);
        for(File cachedFile : cacheDir.listFiles())
        	if(!cachedFile.delete())
        		Log.e("fileshare","cannot delete cached file:"+cachedFile.getName());
    }
    
    /*ͨ��startActivityForResult����������Activity�˳�����õĻص�����*/
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case REQUEST_ENABLE_BLUETOOTH:
            if (resultCode == Activity.RESULT_OK) {
                if(connectionMgr == null){
                	connectionMgr = new ConnectionManager(this,handler,listenPort);
            		connectionMgr.start();
                }
            } else {
                Toast.makeText(this, "�����޷�����", Toast.LENGTH_SHORT).show();
                if(!ISEMULATOR)
                	finish();
            }
            break;
        /*��ӱ��豸�Ĺ����ļ������ҵ��ļ����б���*/
        case REQUEST_ADD_FILES:      	
        	if(resultCode == Activity.RESULT_OK){
        		Log.i("fileshare","get result from FileExplorer");
        		ArrayList<String> files = new ArrayList<String>();
        		String[] sharedFiles = data.getStringArrayExtra("filelist");
        		Log.i("fileshare","file number:"+sharedFiles.length);
        		for(int i = 0;i < sharedFiles.length;i++)
        			files.add(sharedFiles[i]);
        		
        		addSharedFile(files);
        	}
        	break;
        }
    }
    
    /*�����б��е���һ��ᵯ����ݲ˵�������˵��е���һ�����ô˺������д���*/
    @Override
    public boolean onContextItemSelected(MenuItem item){
    	AdapterView.AdapterContextMenuInfo menuInfo;
        menuInfo =(AdapterView.AdapterContextMenuInfo)item.getMenuInfo();  
    	switch(item.getItemId()){
    	case DEVICE_LIST_CONTEXT_MENU_CONNECT:           
            Device d = devices.get(menuInfo.position);
            if(d.device == null){
            	Log.i("FileShare", "in connect() to emulator" + d.port);
            	if(!d.connection_state)
                	connectionMgr.connectEmulator(d.port);
                else
                	Toast.makeText(this, "����emulator-"+d.port+"����", Toast.LENGTH_SHORT).show();
            }
            else{
            	BluetoothDevice device = d.device;
                Log.i("FileShare", "in connect() "+device.getAddress());
                if(!d.connection_state)
                	connectionMgr.connect(bluetoothAdapter.getRemoteDevice(device.getAddress()));
                else
                	Toast.makeText(this, "����"+device.getName()+"����", Toast.LENGTH_SHORT).show();
            }
            
    		return true;
    	case MYFILES_LIST_CONTEXT_MENU_ADDTAG:
    		addTagFile = myFiles.get(menuInfo.position);
    		
    		inputDialog = new AlertDialog.Builder(this);
    		inputDialog.setTitle("�������ļ���ǩ");
    		inputText = new EditText(this);
            inputDialog.setView(inputText);
            inputDialog.setPositiveButton("ȷ��", new DialogInterface.OnClickListener(){
            	public void onClick(DialogInterface d,int arg){
            		String inputTag = inputText.getText().toString();
            		if(!inputTag.equals("")){
            			String[] tags = inputTag.split(" ");                
                        for(String tag : tags)
                        	if(!addTagFile.tags.contains(tag))
                        		addTagFile.tags.add(tag);
                        
                        HashMap<String,Object> msgData = new HashMap<String,Object>();
                        msgData.put("file", addTagFile);
                        msgData.put("tags", addTagFile.tags);
                        for(Device dev : devices)
                        	if(dev.connection_state)
                        		communicationMgr.sendMessage("emulator-"+dev.port, new FSMessage(FSMessage.UPDATE_TAG,msgData));
            		}
                    
            	}
            });
            inputDialog.show(); 
    		return true;
    	case OTHERSFILES_LIST_CONTEXT_MENU_INFO:   
    		FSFile file = othersFiles.get(menuInfo.position);
    		String tags = "";
    		for(String tag : file.tags){
    			tags += tag;
    			tags += " ";
    		}
    		
    		inputDialog = new AlertDialog.Builder(this);
    		inputDialog.setTitle("��ϸ��Ϣ");
    		textView = new TextView(this);
    		textView.setText("actualOwner: "+file.actualOwner+"\n"+
    				         "transitDevice: "+file.transitDevice+"\n"+
    				         "hop: "+file.hop+"\n"+
    				         "gotten:"+file.gotten+"\n"+
    				         "cached:"+file.cached+"\n"+
    				         "tags:"+tags);
    		inputDialog.setView(textView);
    		inputDialog.setPositiveButton("ȷ��", null);
    		inputDialog.show();
    		return true;
    	case OTHERSFILES_LIST_CONTEXT_MENU_GET:
    		file = othersFiles.get(menuInfo.position);
    		getFile(file);
    		
    		return true;
    	case DEVICE_LIST_CONTEXT_MENU_DISCONNECT:{
    		d = devices.get(menuInfo.position);
    		String deviceID = "emulator-"+d.port;
    		if(d.connection_state){
    			FSMessage message = new FSMessage(FSMessage.DEVICE_DEPARTURE,null);
    			communicationMgr.sendMessage(deviceID, message);
        		
        		this.disconnect(deviceID);
    		}
    		
    		return true;
    	}
    	case MYFILES_LIST_CONTEXT_MENU_PREVIEW:{
    		String path = this.myFiles.get(menuInfo.position).fileName;
    		Intent intent = new Intent(this,ImagePreview.class);
    		intent.putExtra(ImagePreview.IMAGE_SOURCE, path);
    		this.startActivity(intent);
    		return true;
    	}
    	case OTHERSFILES_LIST_CONTEXT_MENU_PREVIEW:{
    		FSFile imageFile = this.othersFiles.get(menuInfo.position);
    		
    		if(!imageFile.gotten){
    			Toast.makeText(this, "�ļ���δ��ȡ", Toast.LENGTH_LONG).show();
    		}
    		else{
    			String path = FileShare.APPDIR + "/" + imageFile.actualOwner + "-" + this.getFileName(imageFile.fileName);
        		Intent intent = new Intent(this,ImagePreview.class);
        		intent.putExtra(ImagePreview.IMAGE_SOURCE, path);
        		this.startActivity(intent);
    		}
    		return true;
    	}
    	}   	
        return false;
    }
    
    
    /*
     * ���ѡ��˵�
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mymenu, menu);
        return true;
    }
    
    /*ѡ��˵��е�ĳһ����ʱ����*/
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
//        case R.id.scan:
//            scanDevices();
//            return true;
//        case R.id.discoverable:
//        	makeDiscoverable();
//        	return true;
        case R.id.share:
        	Intent intent = new Intent(this,FileExplorer.class);
        	startActivityForResult(intent,REQUEST_ADD_FILES);
            return true;
        case R.id.search:
        	search();
        	return true;
        case R.id.add_emulator:
        	addEmulator();
        	return true;
        case R.id.about:
        	showAboutInfo();
        	return true;
        }
        return false;
    }
    
    /*���ӡ���ȡ�ļ����󡱵����*/
    private synchronized void increaseGetFileRequestNum(){
    	getFileRequestNum++;
    }
    /*���ӡ������ļ����󡱵����*/
    private synchronized void increaseFileSearchRequestNum(){
    	fileSearchRequestNum++;
    }
    
    /*
     * ��ȡָ���ļ�
     */
    private void getFile(FSFile file){
    	if(file.gotten || file.cached){
			Toast.makeText(this, "�ļ�:"+getFileName(file.fileName)+"���ڱ���", Toast.LENGTH_LONG).show();
			
			if(file.cached){
				moveFile(file);
				file.cached = false;
				file.gotten = true;
			}
		}
		else{
			ArrayList<String> notReq = new ArrayList<String>();
			for(Device dev : devices){
				String deviceID = "emulator-"+dev.port;
				if(!deviceID.equals(file.transitDevice) && dev.connection_state)
					notReq.add(deviceID);
			} 			
			
			increaseGetFileRequestNum();
    		GetFileRequest request = new GetFileRequest(thisID,getFileRequestNum,file);
    		FSMessage message = new FSMessage(FSMessage.GET_FILE,request);
    		communicationMgr.initNotRequested(request,notReq);
    		communicationMgr.sendMessage(file.transitDevice, message);
		}
    }
    
    /*
     * ���������ļ��Ի������û�����Ҫ���ҵ��ļ��ı�ǩ
     */
    private void search(){
    	int devConNum = 0;
    	for(Device d : devices)
    		if(d.connection_state)
    			devConNum++;
    	if(devConNum == 0){
    		inputDialog = new AlertDialog.Builder(this);
    		inputDialog.setTitle("��ʾ");
    		TextView text = new TextView(this);
    		text.setText("û�����κ��豸���ӣ��޷������ļ�");
    		inputDialog.setView(text);
    		inputDialog.setPositiveButton("ȷ��", null);
    		inputDialog.show();
    		return;
    	}
    	
    	inputDialog = new AlertDialog.Builder(this);
        inputDialog.setTitle("�������ļ�����ǩ��");
        inputText = new EditText(this);
        inputDialog.setView(inputText);
        inputDialog.setPositiveButton("ȷ��", new DialogInterface.OnClickListener(){
        	public void onClick(DialogInterface d,int arg){
        		String tag = inputText.getText().toString();
        		Log.i("fileshare","get tag in search:"+tag);
        		
        		//int notResponse = 0;
        		ArrayList<String> notResponse = new ArrayList<String>();
        		increaseFileSearchRequestNum();
        		FileSearchRequest request = new FileSearchRequest(FileShare.thisID,fileSearchRequestNum,tag);
        		FSMessage message = new FSMessage(FSMessage.SEARCH_FILE,request);
        		for(Device dev : devices)
        			if(dev.connection_state){
        				//notResponse++;
        				notResponse.add("emulator-"+dev.port);
        				communicationMgr.sendMessage("emulator-"+dev.port, message);
        			}
        		
        		initNotResponse(notResponse);
        		
        		scanProgress = new ProgressDialog(FileShare.this);
                scanProgress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                scanProgress.setMessage("���ڲ��ҷ��ϱ�ǩ��\""+tag+"\"���ļ�");
                scanProgress.show();
        	}
        });
        inputDialog.show();
    }
    
    /*
     * ��ʾ�����ڳ��򡱶Ի���
     */
    private void showAboutInfo(){
    	inputDialog = new AlertDialog.Builder(this);
    	inputDialog.setTitle("���ڳ���");
    	TextView info = new TextView(this);
    	info.setText("���ߣ�����\n�汾��1.0");
    	inputDialog.setView(info);
    	inputDialog.setPositiveButton("ȷ��", null);
    	inputDialog.show();
    }
    
    /*
     * ��ӹ����ļ�
     */
    private void addSharedFile(ArrayList<String> files){
    	if(files.size() > 0){
    		ArrayList<FSFile> filelist = new ArrayList<FSFile>();
    		
    		for(String fileName : files){
    			FSFile newFile = new FSFile(fileName,thisID,thisID,0);
       		
        		myFiles.add(newFile);
        		
        		filelist.add(newFile);
        		
        		Map<String,Object> file = new HashMap<String,Object>();
        		file.put("fileName", getFileName(fileName));
        		myFilesList.add(file);
        		tabs.setCurrentTab(this.TAB_MYFILE);
        		my_file_list.setVisibility(View.GONE);
        		SimpleAdapter adapter = (SimpleAdapter)my_file_list.getAdapter();
                adapter.notifyDataSetChanged();
                my_file_list.setVisibility(View.VISIBLE);
    		}
    		
    		/*֪ͨ���������ӵ��豸���µĹ����ļ�*/
    		for(Device d : devices){
            	if(d.connection_state)
            		sendFileList(filelist,"emulator-"+d.port);
            }
    	}
    }
    
    /*
     * ���һ��ģ�����豸
     */
    private void addEmulator(){
    	inputDialog = new AlertDialog.Builder(this);
    	inputDialog.setTitle("����Ҫ��ӵ��豸�Ķ˿ں�");
    	inputText = new EditText(this);
        inputDialog.setView(inputText);
        inputDialog.setPositiveButton("ȷ��", new DialogInterface.OnClickListener(){
        	public void onClick(DialogInterface di,int arg){
        		int port = Integer.valueOf(inputText.getText().toString());
        		Device d = new Device(null,false,port);
        		devices.add(d);
        		
        		Map<String,Object> device = new HashMap<String,Object>();
        		device.put("device_name", "emulator-"+port);
        		device.put("connection_state", "δ����");
        		devicesList.add(device);
        		tabs.setCurrentTab(TAB_DEVICE);
        		device_list.setVisibility(View.GONE);
        		SimpleAdapter adapter = (SimpleAdapter)device_list.getAdapter();
                adapter.notifyDataSetChanged();
                device_list.setVisibility(View.VISIBLE);
        	}
        });
        inputDialog.show();
    }
    
    /*
     * ɨ������ӵ��豸��ģ��������˺���
     */
    private void scanDevices(){
    	scanProgress = new ProgressDialog(this);
        scanProgress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        scanProgress.setMessage("����ɨ���豸");
        
    	device_list.setVisibility(View.GONE);
    	scanProgress.show();
        bluetoothAdapter.startDiscovery();  
    	device_list.setVisibility(View.VISIBLE);
    }
    
    /*
     * ������豸�������豸����
     */
    private void makeDiscoverable() {
        if (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }
    
    /* scanReceiver��ɨ�������豸ʱ����BluetoothDevice.ACTION_FOUND
     * ��BluetoothAdapter.ACTION_DISCOVERY_FINISHED�㲥
     */
    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            /*����һ�������豸ʱ��������ӽ��豸�б�*/
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Device bd = new Device(device,false,-1);
                if (!devices.contains(bd)) {
                	Log.i("device1",bd.device.getName());
                	if(devices.size() > 0){
                		Log.i("device2",devices.get(0).device.getName());
                    	Log.i("device3",""+devices.get(0).device.getName().equalsIgnoreCase(bd.device.getName()));
                	}
                	
                	Map<String,Object> d = new HashMap<String,Object>();
                	d.put("device_name", device.getName());
                	d.put("connection_state", "δ����");
                	devicesList.add(d);    
                	devices.add(bd);
                }
            /*ɨ����̽����������豸�б�*/
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                scanProgress.cancel();
               	if(device_list.getAdapter().getCount() == 0)
               		Toast.makeText(FileShare.this, "δ���ֿ����ӵ��豸", Toast.LENGTH_SHORT).show();
               	else{
               		SimpleAdapter adapter = (SimpleAdapter)device_list.getAdapter();
                    adapter.notifyDataSetChanged();
               	}
            }
        }
    };
    
    /*
     * ����ConnectionManager��CommunicationManager���͹�������Ϣ��
     * ��ʾ��ʾ���ݲ�����UI
     */
    private final Handler handler = new Handler() {
        @Override
        public synchronized void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_CONNECTION_FAIL:
            	Toast.makeText(getApplicationContext(), "����"+((BluetoothDevice)msg.obj).getName()+"ʧ��", Toast.LENGTH_LONG).show();
            	break;
            case MESSAGE_CONNECTION_SUCCESS:
            	BluetoothDevice bd = (BluetoothDevice)((HashMap<String,Object>)msg.obj).get("device");
            	Device d = new Device(bd,true,-1);
            	for(int i = 0;i < devices.size();i++){
            		if(devices.get(i).equals(d)){
            		  Map<String,Object> device = (Map<String,Object>)device_list.getItemAtPosition(i);
                      device_list.setVisibility(View.GONE);
                      device.remove("connection_state");
                      device.put("connection_state", "������");
                      SimpleAdapter adapter = (SimpleAdapter)device_list.getAdapter();
                      adapter.notifyDataSetChanged();
                      device_list.setVisibility(View.VISIBLE);
                      
                      devices.get(i).connection_state = true;
                      
                      Toast.makeText(getApplicationContext(), "����"+bd.getName()+"�ɹ�", Toast.LENGTH_LONG).show();
                      
                      break;
            		}
            	}
            	break;
            case MESSAGE_CONNECTION_EMULATOR_SUCCESS:
            	d = new Device(null,true,msg.arg1);
            	if(devices.contains(d) && !devices.get(devices.indexOf(d)).connection_state){
            		Map<String,Object> device = (HashMap<String,Object>)device_list.getItemAtPosition(devices.indexOf(d));
                    device_list.setVisibility(View.GONE);
                    device.remove("connection_state");
                    device.put("connection_state", "������");
                    tabs.setCurrentTab(TAB_DEVICE);
                    SimpleAdapter adapter = (SimpleAdapter)device_list.getAdapter();
                    adapter.notifyDataSetChanged();
                    device_list.setVisibility(View.VISIBLE);
                    
                    devices.get(devices.indexOf(d)).connection_state = true;
                    
                    communicationMgr.newSocket("emulator-"+msg.arg1, (Socket)msg.obj); 
                    
            	}
            	else if(!devices.contains(d)){
            		devices.add(d);
            		
            		Map<String,Object> device = new HashMap<String,Object>();
            		device.put("device_name", "emulator-"+msg.arg1);
            		device.put("connection_state", "������");
            		devicesList.add(device);
            		tabs.setCurrentTab(TAB_DEVICE);
            		device_list.setVisibility(View.GONE);
            		SimpleAdapter adapter = (SimpleAdapter)device_list.getAdapter();
                    adapter.notifyDataSetChanged();
                    device_list.setVisibility(View.VISIBLE);
                    
                    communicationMgr.newSocket("emulator-"+msg.arg1, (Socket)msg.obj); 
            	}
            	Log.i("fileshare",((Socket)msg.obj).getInetAddress().getHostAddress());
                Toast.makeText(getApplicationContext(), "����emulator-"+msg.arg1+"�ɹ�", Toast.LENGTH_LONG).show();
                
                /*���ݹ����ļ��б�*/
                ArrayList<FSFile> filelist = new ArrayList<FSFile>();
                for(FSFile file : myFiles)
                	filelist.add(file);
                for(FSFile file : othersFiles)
                	filelist.add(file);
                if(filelist.size() > 0)
                	sendFileList(filelist,"emulator-"+msg.arg1);
            	break;
            case MESSAGE_CONNECTION_EMULATOR_FAIL:
            	Toast.makeText(getApplicationContext(), "����emulator-"+msg.arg1+"ʧ��", Toast.LENGTH_LONG).show();
            	break;
            case MESSAGE_ADD_FILES:
            	ArrayList<FSFile> addedFiles = (ArrayList<FSFile>)msg.obj;
            	String deviceID = null;
            	boolean fileAdded = false;
            	for(FSFile file : addedFiles){
            		if(!myFiles.contains(file) && !othersFiles.contains(file) && !file.actualOwner.equals(thisID)){
            			HashMap<String,Object> addedfile = new HashMap<String,Object>();
                		addedfile.put("fileName", getFileName(file.fileName));
                        othersFilesList.add(addedfile);
                        
                        //file.transitDevice = deviceID;         
                        file.hop++;
                        file.cached = false;
                        file.gotten = false;
                        othersFiles.add(file);    
                        
                        fileAdded = true;
            		}
            		else if(othersFiles.contains(file)){
            			FSFile oldfile = othersFiles.get(othersFiles.indexOf(file));
            			oldfile.tags = file.tags;
            			if(file.hop + 1 < oldfile.hop){
            				oldfile.hop = file.hop + 1;
            				oldfile.transitDevice = file.transitDevice;
            			}
            		}
            	}
            	
            	if(fileAdded){
            		tabs.setCurrentTab(TAB_OTHERFILE);
            		others_file_list.setVisibility(View.GONE);
             		SimpleAdapter adapter = (SimpleAdapter)others_file_list.getAdapter();
                    adapter.notifyDataSetChanged();
                    others_file_list.setVisibility(View.VISIBLE);
              
                   // Toast.makeText(getApplicationContext(), deviceID+"���µĹ����ļ�", Toast.LENGTH_LONG).show();
            	}
               
            	break;
            case MESSAGE_DEVICE_DEPARTURE:
            	deviceID = (String)msg.obj;
//            	for(Device device : devices){
//            		if(deviceID.equals("emulator-"+device.port)){
//            			devicesList.remove(devices.indexOf(device));
//            			tabs.setCurrentTab(TAB_DEVICE);
//            			device_list.setVisibility(View.GONE);
//                		SimpleAdapter adapter = (SimpleAdapter)device_list.getAdapter();
//                        adapter.notifyDataSetChanged();
//                        device_list.setVisibility(View.VISIBLE);
//                        
//                        ArrayList<FSFile> othersFilesCopy = (ArrayList<FSFile>)othersFiles.clone();
//                       
//                        for(FSFile file : othersFilesCopy){
//                        	if(!file.gotten && !file.cached && (file.actualOwner.equals(deviceID) || file.transitDevice.equals(deviceID))){
//                        		othersFilesList.remove(othersFiles.indexOf(file));                      		                             
//                                othersFiles.remove(file);
//                        	}
//                        }
//                        
//                        tabs.setCurrentTab(TAB_OTHERFILE);
//                        others_file_list.setVisibility(View.GONE);
//                 		adapter = (SimpleAdapter)others_file_list.getAdapter();
//                        adapter.notifyDataSetChanged();
//                        others_file_list.setVisibility(View.VISIBLE);
//                        
//                        devices.remove(device);
//                        
//                        communicationMgr.closeSocket(deviceID);
//                        
//                        break;
//            		}
//            	}
            	disconnect(deviceID);
            	Toast.makeText(getApplicationContext(), deviceID+" �뿪", Toast.LENGTH_LONG).show();
            	break;
            case MESSAGE_GET_FILE_HIT:
            	HashMap<String,Object> data = (HashMap<String,Object>)msg.obj;
            	deviceID = (String)data.get("deviceID");
            	FSFile requestFile = (FSFile)data.get("file");
            	
            	Toast.makeText(getApplicationContext(), deviceID+" �����ļ���"+getFileName(requestFile.fileName)+".�ڱ���", Toast.LENGTH_LONG).show();
            	break;
            case MESSAGE_GET_FILE_NOTHIT:
                data = (HashMap<String,Object>)msg.obj;
            	deviceID = (String)data.get("deviceID");
            	String transitDevice = (String)data.get("transitDevice");
            	requestFile = (FSFile)data.get("file");
            	
            	Toast.makeText(getApplicationContext(), deviceID+" �����ļ���"+getFileName(requestFile.fileName)+".�ļ����ڱ���.\nת������"+transitDevice, Toast.LENGTH_LONG).show();
            	break;
            case MESSAGE_GET_FILE_MINE:
            	data = (HashMap<String,Object>)msg.obj;
            	deviceID = (String)data.get("deviceID");
            	requestFile = (FSFile)data.get("file");
            	
            	othersFiles.get(othersFiles.indexOf(requestFile)).gotten = true;
            	othersFiles.get(othersFiles.indexOf(requestFile)).hop = 0;
            	
            	Toast.makeText(getApplicationContext(), "��������ļ�:"+getFileName(requestFile.fileName)+"����"+deviceID+"���䵽����", Toast.LENGTH_LONG).show();
            	break;
            case MESSAGE_GET_FILE_OTHERS:
            	data = (HashMap<String,Object>)msg.obj;
            	deviceID = (String)data.get("deviceID");
            	transitDevice = (String)data.get("transitDevice");
            	requestFile = (FSFile)data.get("file");
            	
            	othersFiles.get(othersFiles.indexOf(requestFile)).cached = true;
            	othersFiles.get(othersFiles.indexOf(requestFile)).hop = 0;
            	
            	Toast.makeText(getApplicationContext(), "�� "+transitDevice+" ��ת�����ļ�\n��������ļ�:"
						+getFileName(requestFile.fileName)+"����"+deviceID+"���䵽����", Toast.LENGTH_LONG).show();
            	break;
            case MESSAGE_FILE_STORED:
            	String fileName = (String)msg.obj;
            	Toast.makeText(getApplicationContext(), "�ļ���"+fileName+" �ѱ��浽"+APPDIR, Toast.LENGTH_LONG).show();
            	break;
            case MESSAGE_FILE_NOTEXIST:
            	GetFileRequest request = (GetFileRequest)msg.obj;
            	FSFile file = request.file;
            	
            	othersFilesList.remove(othersFiles.indexOf(file));
            	tabs.setCurrentTab(TAB_OTHERFILE);
        		others_file_list.setVisibility(View.GONE);
        		SimpleAdapter adapter = (SimpleAdapter)others_file_list.getAdapter();
                adapter.notifyDataSetChanged();
                others_file_list.setVisibility(View.VISIBLE);
                
                othersFiles.remove(file);
                
                Toast.makeText(getApplicationContext(), "������ļ���\""+getFileName(file.fileName)+"\"������", Toast.LENGTH_LONG).show();
            	break;
            case MESSAGE_SEARCH_RESULT:
            	Log.i("fileshare","get search result");
            	ArrayList<FSFile> result = (ArrayList<FSFile>)msg.obj;
            	deviceID = "emulator-"+msg.arg1;
            	
            	synchronized(this){
            		notResponse.remove(deviceID);
            		
            		for(FSFile matchedFile : result){
            			matchedFile.transitDevice = deviceID;
            			matchedFile.hop++;
            			matchedFile.gotten = matchedFile.cached = false;
            			if(!searchResult.contains(matchedFile)){         				
            				searchResult.add(matchedFile);        				
            			}
            			if(!othersFiles.contains(matchedFile)){
            				HashMap<String,Object> addedfile = new HashMap<String,Object>();
                    		addedfile.put("fileName", getFileName(matchedFile.fileName));
                            othersFilesList.add(addedfile);
                            
                            othersFiles.add(matchedFile);
            			}
            			else if(matchedFile.hop < othersFiles.get(othersFiles.indexOf(matchedFile)).hop){
            				int index = othersFiles.indexOf(matchedFile);
            				FSFile oldFile = othersFiles.get(index);
            				oldFile.transitDevice = deviceID;
            				oldFile.hop = matchedFile.hop;
            				
            			}
            		}
            		if(notResponse.size() == 0){
            			scanProgress.cancel();
                    	
                    	inputDialog = new AlertDialog.Builder(FileShare.this);
                    	inputDialog.setTitle("�������");
                    	
                    	if(searchResult.size() == 0){
                    		TextView text = new TextView(FileShare.this);
                    		text.setText("û���ҵ�����Ҫ����ļ�");
                    		inputDialog.setView(text);
                    		inputDialog.setPositiveButton("ȷ��", null);
                    		inputDialog.show();
                    	}
                    	else{
                    		String[] files = new String[searchResult.size()];
                    		for(int i = 0;i < files.length;i++){
                    			FSFile f = searchResult.get(i);
                    			String tags = "";
                    			for(String tag : f.tags)
                    				tags = tags + " " + tag;
                    			files[i] = getFileName(f.fileName)+"(��ǩ��"+tags+")";
                    		}
                    		inputDialog.setItems(files, new DialogInterface.OnClickListener(){
                    			public void onClick(DialogInterface dialog, int which) {
                                    FSFile chosenFile = searchResult.get(which);
                                    getFile(chosenFile);
                                }
                    		});
                    		inputDialog.show();
                    		
                    		((SimpleAdapter)others_file_list.getAdapter()).notifyDataSetChanged();
                    	}
            		}
            	}
            	          	         	
            	break;
            case MESSAGE_UPDATE_TAG:
            	HashMap<String,Object> msgData = (HashMap<String,Object>)msg.obj;
            	FSFile updateFile = (FSFile)msgData.get("file");
            	ArrayList<String> newTags = (ArrayList<String>)msgData.get("tags");
            	
            	othersFiles.get(othersFiles.indexOf(updateFile)).tags = newTags;
            	
            	Toast.makeText(getApplicationContext(), "�ļ���\""+getFileName(updateFile.fileName)+"\"�ı�ǩ������", Toast.LENGTH_LONG).show();
            	break;
            }
        }
    };
    
    /*�������豸ʱ���䷢�ͱ��豸��֪�Ĺ����ļ�*/
    private void sendFileList(ArrayList<FSFile> filelist,String deviceID){
    	FSMessage message = new FSMessage(FSMessage.ADD_FILES,filelist);
    	communicationMgr.sendMessage(deviceID, message);
    }
    
    /*���ļ�·���н������ļ���*/
    private String getFileName(String file){
		String[] fileNameSplit = file.split("/");
		return fileNameSplit[fileNameSplit.length-1];
	}
    
    /*��������ļ���������Ŀ¼��*/
    private void moveFile(FSFile file){
    	File oldFile = new File(this.CACHEDIR+"/"+file.actualOwner+"-"+getFileName(file.fileName));
    	File newFile = new File(this.APPDIR+"/"+file.actualOwner+"-"+getFileName(file.fileName));
    	
    	try{
    		if(!newFile.exists())
        		newFile.createNewFile();
    		
    		FileInputStream fis = new FileInputStream(oldFile);
    		FileOutputStream fos = new FileOutputStream(newFile);
    		
    		int bytes = 0;
    		byte[] buffer = new byte[1024];
    		while((bytes = fis.read(buffer)) > 0){
    			if(bytes == 1024)
    				fos.write(buffer);
    			else
    				fos.write(buffer, 0, bytes);
    		}
    		
    		if(fis != null)
    			fis.close();
    		if(fos != null){
    			fos.flush();
    			fos.close();
    		}
    		oldFile.delete();
    	}catch(IOException e){
    		Log.e("fileshare","error in moveFile() for file:"+file.fileName,e);
    	}
    	  	
    }
    
    /*�����ļ�ʱ�����ã���¼�����л�δ�ظ����豸*/
    private void initNotResponse(ArrayList<String> notRes){
		synchronized(this){
			searchResult.clear();
			notResponse = notRes;
		}
	}
    
    /*��ָ���豸�Ͽ�����*/
    private void disconnect(String deviceID){
    	for(Device device : devices){
    		if(deviceID.equals("emulator-"+device.port)){
    			devicesList.remove(devices.indexOf(device));
    			tabs.setCurrentTab(TAB_DEVICE);
    			device_list.setVisibility(View.GONE);
        		SimpleAdapter adapter = (SimpleAdapter)device_list.getAdapter();
                adapter.notifyDataSetChanged();
                device_list.setVisibility(View.VISIBLE);
                
                ArrayList<FSFile> othersFilesCopy = (ArrayList<FSFile>)othersFiles.clone();
               
                for(FSFile file : othersFilesCopy){
                	if(!file.gotten && !file.cached && (file.actualOwner.equals(deviceID) || file.transitDevice.equals(deviceID))){
                		othersFilesList.remove(othersFiles.indexOf(file));                      		                             
                        othersFiles.remove(file);
                	}
                }
                
                tabs.setCurrentTab(TAB_OTHERFILE);
                others_file_list.setVisibility(View.GONE);
         		adapter = (SimpleAdapter)others_file_list.getAdapter();
                adapter.notifyDataSetChanged();
                others_file_list.setVisibility(View.VISIBLE);
                
                devices.remove(device);
                
                communicationMgr.closeSocket(deviceID);
                
                break;
    		}
    	}
    }
   
}