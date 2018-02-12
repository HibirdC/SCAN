#include "stdafx.h"
#include<WinSock2.h>
#include<stdio.h>
#include<Windows.h>
#include "./json/json.h"
#include "sqlite3.h"
using namespace std;

#pragma comment(lib,"ws2_32.lib")
#ifdef _DEBUG 
#pragma comment(lib,"json.lib")
#pragma comment(lib,"sqlite3.lib")
#else
#pragma comment(lib,"json_rel.lib")
#pragma comment(lib,"sqlite3_rel.lib")
#endif

#define PORT	1987		//�����˿�
#define BUFSIZE 1024		//���ݻ�������С
void ParseJson(char *str);
typedef struct
{
	SOCKET s;				//�׽��־��
	sockaddr_in addr;		//�Է��ĵ�ַ
} PER_HANDLE_DATA, *PPER_HANDLE_DATA;

typedef struct
{
	OVERLAPPED ol;			//�ص��ṹ
	char buf[BUFSIZE];		//���ݻ�����
	int nOperationType;		//��������
} PER_IO_DATA, *PPER_IO_DATA;

typedef struct {
	std::string Name;
	std::string Code;
	std::string Time;
	std::string Des;
}CodeData;
//�Զ����ע�¼�
#define OP_READ		100
#define OP_WRITE	200

DWORD WINAPI WorkerThread(LPVOID ComPortID)
{
	HANDLE cp = (HANDLE)ComPortID;

	DWORD	btf;
	PPER_IO_DATA pid;
	PPER_HANDLE_DATA phd;
	DWORD   SBytes, RBytes;
	DWORD   Flags;

	while (true)
	{
		//��������ɶ˿ڵ������׽ӿڵȴ�IO׼����
		if (GetQueuedCompletionStatus(cp, &btf, (LPDWORD)&phd,
			(LPOVERLAPPED *)&pid, WSA_INFINITE) == 0){
			return 0;
		}

		//���ͻ��˹ر�ʱ�ᴥ��
		if (0 == btf && (pid->nOperationType == OP_READ || pid->nOperationType == OP_WRITE)) {
			closesocket(phd->s);

			GlobalFree(pid);
			GlobalFree(phd);

			printf("Client closed\n");
			continue;
		}

		WSABUF buf;
		//�ж�IO�˿ڵĵ�ǰ�����¼�(����orд��)
		switch (pid->nOperationType){
		case OP_READ:

			pid->buf[btf] = '\0';
			printf("Recv: %s\n", pid->buf);
			ParseJson(pid->buf);
			char sendbuf[BUFSIZE];
			sprintf(sendbuf, "%s", "OK");

			//����Ͷ��д���Ĳ���
			buf.buf = sendbuf;
			buf.len = strlen(sendbuf) + 1;
			pid->nOperationType = OP_WRITE;
			SBytes = 0;

			//�ò���ϵͳ�첽�����
			WSASend(phd->s, &buf, 1, &SBytes, 0, &pid->ol, NULL);
			break;
		case OP_WRITE:
			ZeroMemory(&(pid->ol), sizeof(OVERLAPPED));
			ZeroMemory(&(pid->buf), sizeof(pid->buf));
			//����Ͷ�ݶ���Ĳ���
			buf.buf = pid->buf;
			buf.len = BUFSIZE;
			pid->nOperationType = OP_READ;

			RBytes = 0;
			Flags = 0;

			//�õײ��̳߳��첽�����
			WSARecv(phd->s, &buf, 1, &RBytes, &Flags, &pid->ol, NULL);
			break;
		}
	}

	return 0;
}

int executeNoQuery(sqlite3 *db, const char *sql)
{
	sqlite3_stmt *pstmt = NULL;

	if (sqlite3_prepare_v2(db, sql, strlen(sql), &pstmt, NULL) != SQLITE_OK)
	{
		if (pstmt != NULL)
			sqlite3_finalize(pstmt);
		fprintf(stderr, "%s\n", sqlite3_errmsg(db));
		return -1;
	}
	if (sqlite3_step(pstmt) != SQLITE_DONE)
	{
		sqlite3_finalize(pstmt);
		fprintf(stderr, "%s\n", sqlite3_errmsg(db));
		return -1;
	}
	if (pstmt != NULL)
		sqlite3_finalize(pstmt);
	return 0;
}
void ASCIIToUTF8(char cACSII[], char cUTF8[])
{
	//�Ƚ�ASCII��ת��ΪUnicode����
	int nlen = MultiByteToWideChar(CP_ACP, 0, cACSII, -1, NULL, NULL);
	wchar_t *pUnicode = new wchar_t[BUFSIZE];
	memset(pUnicode, 0, nlen*sizeof(wchar_t));
	MultiByteToWideChar(CP_ACP, 0, cACSII, -1, (LPWSTR)pUnicode, nlen);
	wstring wsUnicode = pUnicode;
	//��Unicode����ת��ΪUTF-8����
	nlen = WideCharToMultiByte(CP_UTF8, 0, wsUnicode.c_str(), -1, NULL, 0, NULL, NULL);
	WideCharToMultiByte(CP_UTF8, 0, wsUnicode.c_str(), -1, cUTF8, nlen, NULL, NULL);
}

void executeWithQuery(sqlite3 *db, char ***result, int *col, const char *sql)
{
	int ret, row;
	char *errMsg;

	ret = sqlite3_get_table(db, sql, result, &row, col, &errMsg);
	if (ret != SQLITE_OK)
	{
		fprintf(stderr, "%s\n", errMsg);
		sqlite3_free(errMsg);
		return;
	}
	(*result)[(row + 1)*(*col)] = NULL;
	return;
}
BOOL InitDB()
{
	sqlite3 *db;
	int ret = sqlite3_open("sql.db", &db);
	if (ret != SQLITE_OK)
	{
		printf("Create the database failed .%s \n", sqlite3_errmsg(db));
		sqlite3_close(db);
		return false;
	}
	const char *createSQL = "create table if not exists CodeInfo (Name varchar(36), Code varchar(64), Time varchar(64),Des varchar(64));"; 
	ret = executeNoQuery(db, createSQL);
	if (ret == -1)
	{
		printf("Create the code table failed .root cause : %s \n", sqlite3_errmsg(db));
		sqlite3_close(db);
		return false;
	}
	const char *createUserSQL = "create table if not exists UserInfo (Name varchar(36), Pwd varchar(64),Des varchar(64)); ";
	ret = executeNoQuery(db, createUserSQL);
	if (ret == -1)
	{
		printf("Create the code table failed .root cause : %s \n", sqlite3_errmsg(db));
		sqlite3_close(db);
		return false;
	}
	if (db != NULL)
		sqlite3_close(db);
	return true;
}
BOOL InsertSomething(CodeData data)
{
	char sendCmd[BUFSIZE] = { 0 };
	sprintf(sendCmd, "insert into CodeInfo(Name,Code,Time,Des)values('%s','%s','%s','%s');", data.Name.c_str(), data.Code.c_str(), data.Time.c_str(), data.Des.c_str());
	char sendUTF8[BUFSIZ];
	ASCIIToUTF8(sendCmd, sendUTF8);
	sqlite3 *db;
	int ret = sqlite3_open("sql.db", &db);
	if (ret != SQLITE_OK)
	{
		printf("Create the database failed .%s \n", sqlite3_errmsg(db));
		sqlite3_close(db);
		return false;
	}
	ret = executeNoQuery(db, sendUTF8);
	if (ret == -1)
	{
		printf("Insert the code table failed .root cause : %s \n", sqlite3_errmsg(db));
		sqlite3_close(db);
		return false;
	}
	if (db != NULL)
		sqlite3_close(db);
	return true;
}
int _tmain(int argc, _TCHAR* argv[])
{
	/*
	*Ĭ����С������
	*/
	HWND hwnd = ::FindWindow(_T("ConsoleWindowClass"), 0);
	if (hwnd)
	{
		// �ÿؼ�̨������С����
		::SendMessage(hwnd, WM_SYSCOMMAND, SC_MINIMIZE, 0);
	}
	WSADATA wsaData;
	/*
	* ����ָ���汾��socket���ļ�
	*/
	WSAStartup(MAKEWORD(2, 2), &wsaData);
	printf("**************************************************\n");
	printf("***********Welcome To Code Scan Server************\n");
	printf("**********Build Version 20170226.0001.2***********\n");
	printf("********Code Scan App Server Start Running********\n");
	printf("**************************************************\n\n");
	if (!InitDB())
	{
		printf("Create database failed .\n");
	}
	//����һ��IO��ɶ˿�
	HANDLE completionPort = CreateIoCompletionPort(INVALID_HANDLE_VALUE, NULL, 0, 0);


	//����һ�������̣߳�������ɶ˿�
	CreateThread(NULL, 0, WorkerThread, completionPort, 0, 0);

	/*
	* ��ʼ�������׽ӿ�
	*/
	SOCKET sockSrv = socket(AF_INET, SOCK_STREAM, 0);
	SOCKADDR_IN addrSrv;
	addrSrv.sin_addr.S_un.S_addr = htonl(INADDR_ANY);
	addrSrv.sin_family = AF_INET;
	addrSrv.sin_port = htons(PORT);

	bind(sockSrv, (SOCKADDR*)&addrSrv, sizeof(SOCKADDR));

	listen(sockSrv, 5);

	/*
	* �ȴ�ͨ��
	*/
	while (1)
	{
		SOCKADDR_IN  addrClient;
		int len = sizeof(SOCKADDR);

		SOCKET sockConn = accept(sockSrv, (SOCKADDR*)&addrClient, &len);

		//Ϊ�����Ӵ���һ��handle����������ɶ˿ڶ���
		PPER_HANDLE_DATA phd = (PPER_HANDLE_DATA)GlobalAlloc(GPTR, sizeof(PER_HANDLE_DATA));
		phd->s = sockConn;
		memcpy(&phd->addr, &addrClient, len);

		CreateIoCompletionPort((HANDLE)phd->s, completionPort, (DWORD)phd, 0);

		//�����д
		PPER_IO_DATA pid = (PPER_IO_DATA)GlobalAlloc(GPTR, sizeof(PER_IO_DATA));

		ZeroMemory(&(pid->ol), sizeof(OVERLAPPED));

		//����Ͷ�ݶ���Ĳ������ò���ϵͳ���̳߳�ȥ��עIO�˿ڵ����ݽ��հ�
		pid->nOperationType = OP_READ;
		WSABUF buf;
		buf.buf = pid->buf;
		buf.len = BUFSIZE;
		DWORD dRecv = 0;
		DWORD dFlag = 0;

		//һ����������Ǳ������ܿͻ������ӣ�����ֻ��Ҫ�첽Recv����
		WSARecv(phd->s, &buf, 1, &dRecv, &dFlag, &pid->ol, NULL);
	}
}

void ParseJson(char *str)
{
	printf("The Packet size : %d \n", strlen(str));

	Json::Reader reader;
	Json::Value value;

	if (reader.parse(str, value))
	{
		const Json::Value arrayObj = value["CodeInfo"];
		for (int i = 0; i < arrayObj.size(); i++)
		{
			if (arrayObj[i].isMember("Name") && arrayObj[i].isMember("Code") && arrayObj[i].isMember("Time") && arrayObj[i].isMember("Des")
				)
			{
				CodeData Info;
				memset(&Info, 0, sizeof(CodeData));
				Info.Name = arrayObj[i]["Name"].asString();
				Info.Time = arrayObj[i]["Time"].asString();
				Info.Des = arrayObj[i]["Des"].asString();
				Info.Code = arrayObj[i]["Code"].asString();
				InsertSomething(Info);
			}
			else
			{
				continue;
			}
		}
	}
}

