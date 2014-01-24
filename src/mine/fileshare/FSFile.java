package mine.fileshare;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * FSFile���ڱ�ʾһ�������ļ���������һ���ļ��Ļ�����Ϣ
 *
 */
public class FSFile implements Serializable{
	String fileName;
	//�ļ���ʵ��ӵ����
	String actualOwner;
	//�ļ�����·���ϵ���ת�豸����������ļ�ʱ����Ӧ��ת�������豸
	String transitDevice;
	//�ļ���ʵ��ӵ���ߴ��ݵ����豸���辭��������
	int hop;
	//�����˹�����ļ��ڱ��豸���Ƿ��и���
	boolean gotten = false;
	//�����˹�����ļ��ڱ��豸���Ƿ��л���
	boolean cached = false;
	//�ļ���ǩ
	ArrayList<String> tags;
	
	public FSFile(String fileName,String actualOwner,String transitDevice,int hop){
		this.fileName = fileName;
		this.actualOwner = actualOwner;
		this.transitDevice = transitDevice;
		this.hop = hop;
		this.tags = new ArrayList<String>();
	}
	
	public boolean equals(Object o){
		FSFile file = (FSFile)o;
		return this.fileName.equals(file.fileName) && this.actualOwner .equals(file.actualOwner);
	}

}
