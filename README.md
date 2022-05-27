# sync-file

This project is written to solve the repeated work of manually uploading the files written locally to the remote server.

# args

args[0] 指定工作空间 ，只允许指定一个工作空间

args[1] 指定conf目录（在idea中不需要设置）

- [x] config
- [x] workspace
- [x] 前置命令，后置命令
- [x] 多目录监控

config.yml 中的 uploadPath 最后的分隔符必须带上，因为是远程的不能区别Windows和Linux