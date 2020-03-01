import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        new Wiz().login("test@123.com", "password")
                 .downloadTo("C:\\wiz");
    }
}
