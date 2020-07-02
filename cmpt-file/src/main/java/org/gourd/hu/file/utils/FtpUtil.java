package org.gourd.hu.file.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;

/**
 * FTP 操作工具类
 *
 * @author gourd.hu
 */
@Slf4j
@Component
public class FtpUtil {
    /**
     * ftp服务器ip
     */
    @Value("${ftp.host}")
    private String host;
    /**
     * ftp服务器端口
     */
    @Value("${ftp.port}")
    private Integer port;
    /**
     * 用户名
     */
    @Value("${ftp.username}")
    private String username;
    /**
     * 密码
     */
    @Value("${ftp.password}")
    private String password;
    /**
     * 存放文件的基本路径
     */
    @Value("${ftp.basePath:}")
    private String basePath;

    /**
     * @param path     上传文件存放在服务器的路径
     * @param filename 上传文件名
     * @param input    输入流
     * @return
     */
    public boolean fileUpload(String path, String filename, InputStream input) {
        FTPClient ftp = new FTPClient();
        try {
            ftp.connect(host, port);
            boolean loginFlag = ftp.login(username, password);
            if(!loginFlag){
                throw new RuntimeException("登录失败");
            }
            // 设置文件编码格式
            ftp.setControlEncoding("UTF-8");
            // ftp通信有两种模式
            // PORT(主动模式)客户端开通一个新端口(>1024)并通过这个端口发送命令或传输数据,期间服务端只使用他开通的一个端口，例如21
            // PASV(被动模式)客户端向服务端发送一个PASV命令，服务端开启一个新端口(>1024),并使用这个端口与客户端的21端口传输数据
            //由于客户端不可控，防火墙等原因，所以需要由服务端开启端口，需要设置被动模式
            ftp.enterLocalPassiveMode();
            // 设置传输方式为流方式
            ftp.setFileTransferMode(FTP.STREAM_TRANSFER_MODE);
            // 取消服务器获取自身Ip地址和提交的host进行匹配
            ftp.setRemoteVerificationEnabled(false);
            // 获取状态码，判断是否连接成功
            if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
                throw new RuntimeException("FTP服务器拒绝连接");
            }
            // 转到上传文件的根目录
            if (StringUtils.isNotBlank(basePath) && !ftp.changeWorkingDirectory(basePath)) {
                throw new RuntimeException("根目录不存在，需要创建");
            }
            // 判断是否存在目录
            if (!ftp.changeWorkingDirectory(path)) {
                String[] dirs = path.split("/");
                // 创建目录
                for (String dir : dirs) {
                    if (StringUtils.isBlank(dir)) {
                        continue;
                    }
                    // 判断是否存在目录
                    if (!ftp.changeWorkingDirectory(dir)) {
                        // 不存在则创建
                        if (!ftp.makeDirectory(dir)) {
                            throw new RuntimeException("子目录创建失败");
                        }
                        // 进入新创建的目录
                        ftp.changeWorkingDirectory(dir);
                    }
                }
                // 设置上传文件的类型为二进制类型
                ftp.setFileType(FTP.BINARY_FILE_TYPE);
                // 上传文件
                if (!ftp.storeFile(filename, input)) {
                    return false;
                }
                input.close();
                ftp.logout();
                return true;
            }


        } catch (Exception e) {
            log.error(e.getMessage(),e);
        } finally {
            if (ftp.isConnected()) {
                try {
                    ftp.disconnect();
                } catch (IOException e) {
                    log.error(e.getMessage(),e);
                }
            }
        }
        return false;
    }

    /**
     * @param filename  文件名，注意！此处文件名为加路径文件名，如：/2015/06/04/aa.jpg
     * @param localPath 存放到本地第地址
     * @return
     */
    public boolean downloadFile(String filename, String localPath) {
        FTPClient ftp = new FTPClient();
        try {
            ftp.connect(host, port);
            boolean loginFlag = ftp.login(username, password);
            if(!loginFlag){
                throw new RuntimeException("登录失败");
            }
            // 设置文件编码格式
            ftp.setControlEncoding("UTF-8");
            // ftp通信有两种模式
            // PORT(主动模式)客户端开通一个新端口(>1024)并通过这个端口发送命令或传输数据,期间服务端只使用他开通的一个端口，例如21
            // PASV(被动模式)客户端向服务端发送一个PASV命令，服务端开启一个新端口(>1024),并使用这个端口与客户端的21端口传输数据
            // 由于客户端不可控，防火墙等原因，所以需要由服务端开启端口，需要设置被动模式
            ftp.enterLocalPassiveMode();
            // 设置传输方式为流方式
            ftp.setFileTransferMode(FTP.STREAM_TRANSFER_MODE);
            // 取消服务器获取自身Ip地址和提交的host进行匹配
            ftp.setRemoteVerificationEnabled(false);
            // 获取状态码，判断是否连接成功
            if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
                throw new RuntimeException("FTP服务器拒绝连接");
            }

            int index = filename.lastIndexOf("/");
            // 获取文件的路径
            String path = filename.substring(0, index);
            // 获取文件名
            String name = filename.substring(index + 1);
            // 判断是否存在目录
            if (!ftp.changeWorkingDirectory(basePath + path)) {
                throw new RuntimeException("文件路径不存在：" + basePath + path);
            }
            // 获取该目录所有文件
            FTPFile[] files = ftp.listFiles();
            for (FTPFile file : files) {
                // 判断是否有目标文件
                if (file.getName().equals(name)) {
                    // 如果找到，将目标文件复制到本地
                    File localFile = new File(localPath + "/" + file.getName());
                    OutputStream out = new FileOutputStream(localFile);
                    ftp.retrieveFile(file.getName(), out);
                    out.close();
                }
            }
            ftp.logout();
            return true;
        } catch (Exception e) {
            log.error(e.getMessage(),e);
            return false;
        } finally {
            if (ftp.isConnected()) {
                try {
                    ftp.disconnect();
                } catch (IOException e) {
                    log.error(e.getMessage(),e);
                    return false;
                }
            }
        }
    }

    public boolean deleteFile(String filename) {
        FTPClient ftp = new FTPClient();
        try {
            ftp.connect(host, port);
            ftp.login(username, password);
            // 设置编码格式
            ftp.setControlEncoding("UTF-8");
            ftp.enterLocalPassiveMode();
            // 获取状态码，判断是否连接成功
            if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
                throw new RuntimeException("FTP服务器拒绝连接");
            }
            int index = filename.lastIndexOf("/");
            // 获取文件的路径
            String path = filename.substring(0, index);
            // 获取文件名
            String name = filename.substring(index + 1);
            // 判断是否存在目录,不存在则说明文件存在
            if (!ftp.changeWorkingDirectory(basePath + path)) {
                return true;
            }
            if (ftp.deleteFile(name)) {
                clearDirectory(ftp, basePath, path);
                ftp.logout();
                return true;
            }
            ftp.logout();
            return false;
        } catch (Exception e) {
            log.error(e.getMessage(),e);
            return false;
        } finally {
            if (ftp.isConnected()) {
                try {
                    ftp.disconnect();
                } catch (IOException e) {
                    log.error(e.getMessage(),e);
                    return false;
                }
            }
        }
    }

    /**
     * @param ftp
     * @param basePath
     * @param path     以path为根，递归清除上面所有空的文件夹，直到出现不为空的文件夹停止，最多清除到basePath结束
     * @throws IOException
     */
    private void clearDirectory(FTPClient ftp, String basePath, String path) throws IOException {
        // 如果路径长度小于2，说明到顶了
        if (path.length() < 2) {
            return;
        }
        // 如果当前目录文件数目小于1则删除此目录
        if (ftp.listNames(basePath + path).length < 1) {
            //删除目录
            log.info("删除目录：" + basePath + path);
            ftp.removeDirectory(basePath + path);
            int index = path.lastIndexOf("/");
            // 路径向上一层
            path = path.substring(0, index);
            // 继续判断
            clearDirectory(ftp, basePath, path);
        }
    }

    public static void main(String[] args) {
        //上传测试--------------------------------------
        /*FileInputStream in;
        try {
            in=new FileInputStream(new File("C:\\Users\\Administrator\\Desktop\\json.png"));
            FtpUtil ftputil=new FtpUtil();
            boolean flag=ftputil.fileUpload("/2015/06/04", "va.jpg", in);
            System.out.println(flag);
        }catch (Exception e) {
            e.printStackTrace();
        }finally {
        }*/
        //下载测试--------------------------------------
        /*String filename="/2015/06/04/aa.jpg";
        String localPath="F:\\";
        FtpUtil ftputil=new FtpUtil();
        ftputil.downloadFile(filename, localPath);*/
        //删除测试--------------------------------------
        FtpUtil ftputil = new FtpUtil();
        boolean flag = ftputil.deleteFile("/2015/06/04/va.jpg");
        System.out.println(flag);
    }
}