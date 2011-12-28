package waveimport;

import com.google.wave.api.WaveService;
import com.google.wave.api.Wavelet;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;

/**
 *
 * @author A. Kaplanov
 */
public class WaveFetch {
    static final String WAVE_RPC="http://localhost:9898/robot/rpc";
    
    public static void main(String[] args) {
        WaveService waveService = new WaveService();
        waveService.setupOAuth(
                "test@localhost",
                "K13TQjuOaDuZrf3coGHzzodZSCv7uuUzmEYMikDwvGJ1fLwF",
                WAVE_RPC);
        try {
            Wavelet wavelet = waveService.fetchWavelet(
                    WaveId.deserialise("localhost!w+1324585280585A"), 
                    WaveletId.deserialise("localhost!conv+root"), WAVE_RPC);
            wavelet.getRootBlip();
        } catch (IOException ex) {
            Logger.getLogger(WaveFetch.class.getName()).log(Level.SEVERE, null, ex);
        }
    }    
}
