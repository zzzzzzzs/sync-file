set CLASS_PATH=../lib/hutool-all-5.7.16.jar;../lib/jsch-0.1.54.jar;../lib/lombok-1.18.24.jar;../lib/snakeyaml-1.23.jar;../lib/commons-io-2.4.jar;../lib/sync-file-1.0-SNAPSHOT.jar;
set EXECUTOR=java.exe -cp %CLASS_PATH%
call %EXECUTOR% com.zzzzzzzs.syncfile.SyncFile %1 /conf/config.yml