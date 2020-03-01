# WizExport
利用为知笔记的 OpenAPI 把笔记下载到本地，支持 markdown 和 html 格式的笔记

# Usage
传入用户名和密码，然后指定一个文档保存位置，启动 main 方法就可以进行文档下载
```Java
public class Main {
    public static void main(String[] args) throws IOException {
        new Wiz().login("test@123.com", "password")
                 .downloadTo("C:\\wiz");
    }
}
```