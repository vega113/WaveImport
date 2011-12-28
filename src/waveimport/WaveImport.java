package waveimport;

import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;
import com.google.appengine.tools.development.ApiProxyLocal;
import com.google.appengine.tools.development.ApiProxyLocalFactory;
import com.google.apphosting.api.ApiProxy;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.util.StringTokenizer;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;

/**
 *
 * @author A. Kaplanov
 */
public final class WaveImport {

    private final String waveServerImportUrl;
    private final String waveServerDomain;
    private final String waveletsJsonDir;

    private WaveImport(String waveServerImportUrl, String waveServerDomain, String waveletsJsonDir) {
        this.waveServerImportUrl = waveServerImportUrl;
        this.waveServerDomain = waveServerDomain;
        this.waveletsJsonDir = waveletsJsonDir;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Use: WaveImport <WaveServerImportUrl> <WaveServerDomain> <WaveletsJsonDir>");
        }
        new WaveImport(args[0], args[1], args[2]).run();
    }

    public void run() {
        ApiProxyEnvironment env = new ApiProxyEnvironment();
        ApiProxyLocal proxy = new ApiProxyLocalFactory().create(env);
        ApiProxy.setDelegate(proxy);
        ApiProxy.setEnvironmentForCurrentThread(new ApiEnvironment());
        File[] files = new File(waveletsJsonDir).listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String name) {
                return name.endsWith("json") /*&& name.indexOf("ZPtZYbC") != -1*/;
            }
        });
        int imported_count = 0;
        int skipped_count = 0;
        int not_imported_count = 0;
        for (File file : files) {
            try {
                System.out.println("Importing " + file.getPath() + "...");
                StringTokenizer st = new StringTokenizer(file.getName(), "#");
                WaveId wave_id=WaveId.of(waveServerDomain, WaveId.deserialise(st.nextToken()).getId());
                WaveletId wavelet_id=WaveletId.of(waveServerDomain, WaveletId.deserialise(st.nextToken()).getId());
                if (importRequest(waveServerImportUrl, wave_id, wavelet_id, readFile(file))) {
                    imported_count++;
                } else {
                    skipped_count++;
                }
            } catch (IOException ex) {
                not_imported_count++;
                ex.printStackTrace(System.err);
            }
            /*
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(WaveImport.class.getName()).log(Level.SEVERE, null, ex);
            }*/
        }
        System.out.println("Imported count " + imported_count);
        System.out.println("Not imported count " + not_imported_count);
        System.out.println("Skipped count " + skipped_count);
    }

    public boolean importRequest(String url, WaveId waveId, WaveletId waveletId, String json) throws IOException {
        URLFetchService url_service = URLFetchServiceFactory.getURLFetchService();
        HTTPRequest request = new HTTPRequest(new URL(url), HTTPMethod.POST);
        request.setHeader(new HTTPHeader("Content-Type", "application/json; charset=UTF-8"));
        request.setHeader(new HTTPHeader("domain", waveServerDomain));
        request.setHeader(new HTTPHeader("waveId", waveId.serialise()));
        request.setHeader(new HTTPHeader("waveletId", waveletId.serialise()));
        request.setPayload(json.getBytes("utf8"));
        HTTPResponse response = url_service.fetch(request);
        if (response.getResponseCode() != 200) {
            throw new IOException(new String(response.getContent()));
        }
        String content = new String(response.getContent());
        System.out.println("... " + content);
        return !content.equals("skipped");
    }

    private static String readFile(File file) throws FileNotFoundException, IOException {
        Reader reader = new FileReader(file);
        StringBuilder sb = new StringBuilder();
        char buf[] = new char[1000];
        for (;;) {
            int ret = reader.read(buf, 0, buf.length);
            if (ret == -1) {
                break;
            }
            sb.append(buf, 0, ret);
        }
        return sb.toString();
    }
}
