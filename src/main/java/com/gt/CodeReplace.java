package com.gt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public class CodeReplace {

	public static void main(String[] args) throws IOException{

		System.out.println(search(" dml"));
		System.out.println(searchAccurate(" dml"));

	}

	public static String search(String searchWord) throws IOException{
		
		if(searchWord.trim().length()>0){
			File file = new File(System.getProperty("user.dir").replace("\\","/")+"/src/main/resources");
			boolean isDirectoryExists = file.exists();

			System.out.println(System.getProperty("user.dir").replace("\\","/")+"/src/main/resources");

			StringBuffer sb = new StringBuffer();
			
			if(isDirectoryExists){

				List<String> files = getFiles(System.getProperty("user.dir").replace("\\","/")+"/src/main/resources");

				for(String f : files){
					String[] arrays = readToString(f).split("\n");
					String scanTag = null;
					Integer startLine = 0;
					Integer count = 1;
					for(int i=0;i<arrays.length;i++){
						if(arrays[i].toString().trim().indexOf(searchWord.trim().split(":")[0])!=-1){
							scanTag = "begin";
							startLine=i;
						}
						if(null != scanTag && scanTag.equals("begin") && arrays[i].toString().indexOf("∈")==0 && i>startLine){
							scanTag = "end";
						}
						if(null != scanTag && scanTag.equals("begin")){
							if(arrays[i].toString().indexOf("∈")==0){
								if(searchWord.contains(":") == true){
									Process p = Runtime.getRuntime().exec("cmd.exe /c Notepad "+f.replace("\\","/"));
								}
								String order = arrays[i].toString().replace("∈", "").split(":")[0];
								String desc = arrays[i].toString().replace("∈", "").split(":")[1];
								sb.append("\n命令："+order+"\n描述："+desc+"\n");
								//System.out.print("\n命令："+order+"\n描述："+desc);
								count=count+1;

							}else{
								sb.append(arrays[i]+"\n");
							}					
						}
						
					}
				}
				return sb.toString();
			}else{
				return "没有找到资源目录";
			}
		}
		return null;
	}

	public static String searchAccurate(String searchWord) throws IOException{
		
		if(searchWord.trim().length()>0){
			File file = new File(System.getProperty("user.dir").replace("\\","/")+"/src/main/resources");
			boolean isDirectoryExists = file.exists();

			StringBuffer sb = new StringBuffer();
			
			if(isDirectoryExists){

				List<String> files = getFiles(System.getProperty("user.dir").replace("\\","/")+"/src/main/resources");

				for(String f : files){
					String[] arrays = readToString(f).split("\n");
					String scanTag = null;
					Integer startLine = 0;
					Integer count = 1;
					for(int i=0;i<arrays.length;i++){
						if(arrays[i].toString().replace("∈","").split(":")[0].trim().equals(searchWord.trim()) || arrays[i].toString().replace("∈","").split(":")[0].trim() == searchWord.trim()){
							scanTag = "begin";
							startLine=i;
						}
						if(null != scanTag && scanTag.equals("begin") && arrays[i].toString().indexOf("∈")==0 && i>startLine){
							scanTag = "end";
						}
						if(null != scanTag && scanTag.equals("begin")){
							if(arrays[i].toString().indexOf("∈")==0){
								String order = arrays[i].toString().replace("∈", "").split(":")[0];
								String desc = arrays[i].toString().replace("∈", "").split(":")[1];
								sb.append("\n命令："+order+"\n描述："+desc+"\n");
								//System.out.print("\n命令："+order+"\n描述："+desc);
								count=count+1;

							}else{
								sb.append(arrays[i]+"\n");
							}					
						}
						
					}
				}
				return sb.toString();
			}else{
				return "没有找到资源目录";
			}
		}
		return null;
	}	
	
	public static ArrayList<String> searchHelper(String searchWord) throws IOException{
		
//		if(searchWord.length()>0){
			File file = new File(System.getProperty("user.dir").replace("\\","/")+"/src/main/resources");
			boolean isDirectoryExists = file.exists();
			
			if(isDirectoryExists){

				List<String> files = getFiles(System.getProperty("user.dir").replace("\\","/")+"/src/main/resources");
				
				ArrayList<String> list = new ArrayList();
				
				for(String f : files){
					String[] arrays = readToString(f).split("\n");
					String scanTag = null;
					Integer startLine = 0;
					Integer count = 1;
					for(int i=0;i<arrays.length;i++){
						if(arrays[i].toString().trim().indexOf(searchWord.trim())!=-1){
							scanTag = "begin";
							startLine=i;
						}
						if(null != scanTag && scanTag.equals("begin") && arrays[i].toString().indexOf("∈")==0 && i>startLine){
							scanTag = "end";
						}
						if(null != scanTag && scanTag.equals("begin")){
							if(arrays[i].toString().indexOf("∈")==0){
								String order = arrays[i].toString().replace("∈", "").split(":")[0];
								String desc = arrays[i].toString().replace("∈", "").split(":")[1];
								list.add(order+" :"+desc);
								//System.out.print("\n命令："+order+"\n描述："+desc);
								count=count+1;

							}else{
								//sb.append(arrays[i]+"\n");
								//System.out.print(arrays[i]+"\n");
							}					
						}
						
					}
				}
				return list;
			}else{
				return new ArrayList<String>();
			}
//		}
//		return new ArrayList<String>();
	}
	
	public static String readToString(String fileName) {  
        String encoding = "UTF-8";  
        File file = new File(fileName);  
        Long filelength = file.length();  
        byte[] filecontent = new byte[filelength.intValue()];  
        try {  
            FileInputStream in = new FileInputStream(file);  
            in.read(filecontent);  
            in.close();  
        } catch (FileNotFoundException e) {  
            e.printStackTrace();  
        } catch (IOException e) {  
            e.printStackTrace();  
        }  
        try {  
            return new String(filecontent, encoding);  
        } catch (UnsupportedEncodingException e) {  
            System.err.println("The OS does not support " + encoding);  
            e.printStackTrace();  
            return null;  
        }  
    }
	
	public static ArrayList<String> getFiles(String path) {
	    ArrayList<String> files = new ArrayList<String>();
	    File file = new File(path);
	    if(file.exists()){

		    File[] tempList = file.listFiles();

		    for (int i = 0; i < tempList.length; i++) {
		        if (tempList[i].isFile()) {
//		              System.out.println("文     件：" + tempList[i]);
		            files.add(tempList[i].toString());
		        }
		        if (tempList[i].isDirectory()) {
//		              System.out.println("文件夹：" + tempList[i]);
		        }
		    }
	    	
	    }else{
	    	files.add(" 资源目录不存在 ");
	    }

	    return files;
	}
}