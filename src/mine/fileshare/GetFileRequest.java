package mine.fileshare;

import android.util.Log;

import java.io.Serializable;

/**
 * ��ȡ�ļ���������Ϣ����������������豸��ʶ���������
 * ��������ļ��Ļ�����Ϣ
 */
public class GetFileRequest implements Serializable{
	String sourceID;
	int requestNum;
	FSFile file;

	public GetFileRequest(String sourceID,int requestNum,FSFile file){
		this.sourceID = sourceID;
		this.requestNum = requestNum;
		this.file = file;
	}
	
	public boolean equals(Object o){
		//Log.i("fileshare","GetFileRequest:call equals");
		GetFileRequest request = (GetFileRequest)o;
		return this.sourceID.equals(request.sourceID) && this.requestNum == request.requestNum;
	}
	
}
