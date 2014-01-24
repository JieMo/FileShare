package mine.fileshare;

import java.io.Serializable;

/**
 * �����ļ����󣬰�������������豸��ʶ���������
 * ��������ļ���ǩ
 */
public class FileSearchRequest implements Serializable{
	String sourceID;
	int requestNum;
	String tag;

	public FileSearchRequest(String sourceID,int requestNum,String tag){
		this.sourceID = sourceID;
		this.requestNum = requestNum;
		this.tag = tag;
	}
	
	public boolean equals(Object o){
		FileSearchRequest request = (FileSearchRequest)o;
		return this.sourceID.equals(request.sourceID) && this.requestNum == request.requestNum;
	}
}
