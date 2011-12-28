package waveimport;

import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;
import com.google.appengine.tools.development.ApiProxyLocal;
import com.google.appengine.tools.development.ApiProxyLocalFactory;
import com.google.appengine.tools.development.LocalServerEnvironment;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.walkaround.wave.server.auth.OAuthCredentials;
import com.google.walkaround.wave.server.auth.OAuthRequestHelper;
import com.google.walkaround.wave.server.auth.OAuthedFetchService;
import com.google.walkaround.wave.server.auth.StableUserId;
import com.google.walkaround.wave.server.auth.UserContext;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.waveprotocol.wave.model.wave.ParticipantId;
import com.google.walkaround.proto.RobotSearchDigest;
import java.io.FileWriter;
import org.json.JSONObject;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;

/**
 *
 * @author A. Kaplanov
 */
public class WaveExport {

    private final String clientId;
    private final String clientSecret;
    private final String userId;
    private final String participant;
    private final String refreshToken;
    private final String accessToken;
    private final String exportDir;

    public WaveExport(String clientId, String clientSecret, String userId, String participant, String refreshToken, String accessToken, String exportDir) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.userId = userId;
        this.participant = participant;
        this.refreshToken = refreshToken;
        this.accessToken = accessToken;
        this.exportDir = exportDir;
    }

    public static void main(String[] args) {
        if (args.length != 5) {
            System.err.println("Use: WaveExport <ClientId> <ClientSecret> <UserId> <Participant> <RefreshToken> <AccessToken> <ExportDir>");
        }
        new WaveExport(args[0], args[1], args[2], args[3], args[4], args[5], args[6]).run();
    }

    public void run() {
        ApiProxyEnvironment env = new ApiProxyEnvironment();
        ApiProxyLocal proxy = new ApiProxyLocalFactory().create(env);
        ApiProxy.setDelegate(proxy);
        ApiProxy.setEnvironmentForCurrentThread(new ApiEnvironment());
        URLFetchService url_service = URLFetchServiceFactory.getURLFetchService();
        UserContext context = new UserContext();
        context.setUserId(new StableUserId(userId));
        context.setParticipantId(new ParticipantId(participant));
        OAuthCredentials cred = new OAuthCredentials(refreshToken, accessToken);
        context.setOAuthCredentials(cred);
        OAuthRequestHelper helper = new OAuthRequestHelper(clientId, clientSecret, context);
        OAuthedFetchService oauth_service = new OAuthedFetchService(url_service, helper);
        RobotApi api = new RobotApi(oauth_service, "https://www-opensocial.googleusercontent.com/api/rpc");
        try {
            int i=0;
            int processed_count = 0;
            int not_processed_count = 0;
            for (;;) {
                List<RobotSearchDigest> list = api.search("after:2000/01/01 before:2012/12/31", i, 100);
                if (list.isEmpty()) {
                    break;
                }
                i += list.size();
                for (RobotSearchDigest digest : list) {
                    System.out.println(digest.getTitle() + ":");
                    try {
                        for (WaveletId waveled_id : api.getWaveView(WaveId.deserialise(digest.getWaveId()))) {
                            File file = new File(exportDir + "/" + digest.getWaveId() + "#" + waveled_id.serialise() + "#json");
                            if (file.exists()) {
                                System.out.println("Skiped " + file.getName());
                            } else {
                                System.out.println("Exporting " + file.getName() + "...");
                                FileWriter w = new FileWriter(file);
                                JSONObject json = api.fetchWaveWithDeltas(WaveId.deserialise(digest.getWaveId()), waveled_id);
                                w.write(json.toString());
                                w.close();
                            }
                        }
                        processed_count++;
                    } catch (IOException ex) {
                        not_processed_count++;
                        System.out.println("Error " + ex.toString());
                        Logger.getLogger(WaveExport.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            System.out.println("Processed count " + processed_count);
            System.out.println("Not processed count " + not_processed_count);
        } catch (IOException ex) {
            Logger.getLogger(WaveExport.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}

class ApiProxyEnvironment implements LocalServerEnvironment {

    @Override
    public File getAppDir() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getAddress() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getPort() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getHostName() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void waitForServerToStart() throws InterruptedException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean enforceApiDeadlines() {
        return false;
    }

    @Override
    public boolean simulateProductionLatencies() {
        return false;
    }
}

class ApiEnvironment implements Environment {

    @Override
    public String getAppId() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getVersionId() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getEmail() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isLoggedIn() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isAdmin() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getAuthDomain() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getRequestNamespace() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Map<String, Object> getAttributes() {
        return new HashMap<String, Object>();
    }
}
