package com.google.zxing.client.android.result;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class DBManager {
	private DBHelper helper;
	private SQLiteDatabase db;
	
	public DBManager(Context context) {
		helper = new DBHelper(context);
		//因为getWritableDatabase内部调用了mContext.openOrCreateDatabase(mName, 0, mFactory);
		//所以要确保context已初始化,我们可以把实例化DBManager的步骤放在Activity的onCreate里
		db = helper.getWritableDatabase();
	}

	public void InsertCodeResult(CodeData Info)
	{
		db.beginTransaction();	//开始事务
        try {
        	db.execSQL("INSERT INTO Scan_CardInfo VALUES(?,?,?,?)", new Object[]{Info.Name, Info.Code, Info.Time,Info.Des});
        	db.setTransactionSuccessful();	//设置事务成功完成
        } finally {
        	db.endTransaction();	//结束事务
        }
	}
	public void InsertUserInfo(String Name,String Pwd)
	{
		db.beginTransaction();	//开始事务
        try {
        	db.execSQL("INSERT INTO Scan_UserInfo VALUES(?,?,?)", new Object[]{Name, Pwd, "Honey"});
        	db.setTransactionSuccessful();	//设置事务成功完成
        } finally {
        	db.endTransaction();	//结束事务
        }
	}
	public Cursor queryTheCursorofResult() {
        Cursor c = db.rawQuery("SELECT * FROM Scan_CardInfo",null);
        return c;
	}
	public Cursor queryTheCursorofUser() {
        Cursor c = db.rawQuery("SELECT * FROM Scan_UserInfo",null);
        return c;
	}
	public String GetUserName()
	{
		String Name="";
		Cursor c = queryTheCursorofUser();
        if (c.moveToNext()) {
        	Name= c.getString(c.getColumnIndex("Name"));
        }
        return Name;
	}
	public void DelUser()
	{
		db.delete("Scan_UserInfo",null, null);
	}
	public List<CodeData> GetAllResult()
	{
		ArrayList<CodeData> InfoList = new ArrayList<CodeData>();
		Cursor c = queryTheCursorofResult();
        while (c.moveToNext()) {
        	CodeData result = new CodeData();
			result.Name  	= c.getString(c.getColumnIndex("Name"));
			result.Code  	= c.getString(c.getColumnIndex("Code"));
			result.Time  	= c.getString(c.getColumnIndex("Time"));
			result.Des  	= c.getString(c.getColumnIndex("Des"));
		    InfoList.add(result);
        }
        c.close();
        return InfoList;
	}
	public void DelAll()
	{
		db.delete("Scan_CardInfo",null, null);
	}
	public void closeDB() {
		db.close();
	}
}
