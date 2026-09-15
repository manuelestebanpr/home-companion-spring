import java.net.URI;
import java.net.http.*;
import java.time.Duration;
class Healthcheck {
    public static void main(String[] args) throws Exception {
        var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080/actuator/health")).timeout(Duration.ofSeconds(4)).build();
        System.exit(client.send(request,HttpResponse.BodyHandlers.discarding()).statusCode()==200?0:1);
    }
}
