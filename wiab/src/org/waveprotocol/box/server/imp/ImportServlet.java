package org.waveprotocol.box.server.imp;

import com.google.gxp.org.apache.xerces.impl.dv.util.Base64;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.waveprotocol.box.server.persistence.AttachmentStore;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.util.logging.Log;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.federation.Proto.ProtocolAppliedWaveletDelta;
import org.waveprotocol.wave.federation.Proto.ProtocolHashedVersion;
import org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta;
import org.waveprotocol.wave.federation.Proto.ProtocolWaveletOperation;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;

/**
 *
 * @author A. Kaplanov
 */
@SuppressWarnings("serial")
@Singleton
public class ImportServlet extends HttpServlet {

    private static final Log LOG = Log.get(ImportServlet.class);
    private final WaveletProvider waveletProvider;
    private final AttachmentStore attachmentStore;
    private HashedVersion hashedVersion = null;
    private StringWriter error = null;
    private String lastParticipant = null;

    @Inject
    private ImportServlet(WaveletProvider waveletProvider, AttachmentStore attachmentStore) {
        this.waveletProvider = waveletProvider;
        this.attachmentStore = attachmentStore;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String domain = request.getHeader("domain");
        WaveId wave_id = WaveId.deserialise(request.getHeader("waveId"));
        WaveletId wavelet_id = WaveletId.deserialise(request.getHeader("waveletId"));
        hashedVersion = null;
        error = new StringWriter();
        boolean skip = false;
        try {
            WaveletName name = WaveletName.of(wave_id, wavelet_id);
            try {
                skip = waveletProvider.getSnapshot(name) != null;
            } catch (WaveServerException ex) {
                Logger.getLogger(ImportServlet.class.getName()).log(Level.SEVERE, null, ex);
            }
            if (!skip) {
                JSONObject exp = new JSONObject(readToString(request.getReader()));
                // Parse deltas from json
                JSONArray raw_deltas = exp.getJSONObject("data").getJSONArray("rawDeltas");
                List<ProtocolAppliedWaveletDelta> deltas = new LinkedList<ProtocolAppliedWaveletDelta>();
                for (int i = 0; i < raw_deltas.length(); i++) {
                    deltas.add(ProtocolAppliedWaveletDelta.parseFrom(Base64.decode(raw_deltas.getString(i))));
                }
                // Apply deltas to wave
                for (ProtocolAppliedWaveletDelta applied_delta : deltas) {
                    ProtocolWaveletDelta delta = ProtocolWaveletDelta.parseFrom(
                            applied_delta.getSignedOriginalDelta().getDelta());
                    ProtocolWaveletDelta.Builder new_delta = ProtocolWaveletDelta.newBuilder(delta);
                    new_delta.setAuthor(convertDomains(delta.getAuthor(), domain));
                    for (int i = 0; i < delta.getOperationCount(); i++) {
                        ProtocolWaveletOperation op = delta.getOperation(i);
                        ProtocolWaveletOperation.Builder new_op = ProtocolWaveletOperation.newBuilder(op);
                        if (op.hasAddParticipant()) {
                            new_op.setAddParticipant(convertDomains(op.getAddParticipant(), domain));
                        } else if (op.hasRemoveParticipant()) {
                            new_op.setRemoveParticipant(convertDomains(op.getRemoveParticipant(), domain));
                        }
                        /* TODO import attachments
                        if (new_op.getMutateDocument().isInitialized()) {
                            MutateDocument.Builder new_doc = MutateDocument.newBuilder(new_op.getMutateDocument());
                            if (new_doc.getDocumentOperation().isInitialized()) {
                                ProtocolDocumentOperation.Builder new_doc_op = ProtocolDocumentOperation.newBuilder(new_doc.getDocumentOperation());
                                for (int j=0; j < new_doc_op.getComponentCount(); j++) {
                                    Component component = new_doc_op.getComponent(j);
                                    Component.Builder new_component = Component.newBuilder(component);
                                    Component.ElementStart.Builder new_element = Component.ElementStart.newBuilder(component.getElementStart());
                                    for (int k=0; k < component.getElementStart().getAttributeCount(); k++) {
                                        Component.KeyValuePair pair = component.getElementStart().getAttribute(k);
                                        if ("key".equals(pair.getKey()) && "attachment_url".equals(pair.getValue())) {
                                            if (++k < component.getElementStart().getAttributeCount()) {
                                                Component.KeyValuePair value_pair = component.getElementStart().getAttribute(k);
                                                if ("value".equals(value_pair.getKey())) {
                                                    URL url = new URL("https://wave.googleusercontent.com/wave" + value_pair.getValue());
                                                    int index = value_pair.getValue().indexOf('?');
                                                    if (index != -1) {
                                                        String[] params = value_pair.getValue().substring(index+1).split("&");
                                                        Map<String, String> map = new HashMap<String, String>();  
                                                        for (String param : params)  
                                                            map.put(param.split("=")[0], param.split("=")[1]);  
                                                        String attachment_id=map.get("key");
                                                        attachmentStore.storeAttachment(name, attachment_id, url.openStream());
                                                        WaveRef wave_ref = WaveRef.of(wave_id, wavelet_id);
                                                        JavaWaverefEncoder.encodeToUriPathSegment(wave_ref);
                                                            String new_url = "http://localhost:9898/" +
                                                            "attachment/" + attachment_id + 
                                                            "?fileName=" + url.getFile() + 
                                                            "&waveRef=" + URLEncoder.encode(JavaWaverefEncoder.encodeToUriPathSegment(wave_ref), "UTF-8");
                                                        Component.KeyValuePair.Builder new_value_pair = Component.KeyValuePair.newBuilder(value_pair);
                                                        new_value_pair.setValue(new_url);
                                                        new_element.setAttribute(k, new_value_pair);
                                                        new_component.setElementStart(new_element);
                                                    }
                                                }
                                            }
                                            break;
                                        }
                                    }
                                    new_doc_op.setComponent(j, new_component);
                                }
                                new_doc.setDocumentOperation(new_doc_op);
                            }
                            new_op.setMutateDocument(new_doc);
                        }*/
                        new_delta.setOperation(i, new_op);
                    }
                    if (delta.getHashedVersion().getVersion() == 0) {
                        String hash = "wave://" + name.waveId.getDomain() + "/" + name.waveId.getId() + "/" + name.waveletId.getId();
                        ProtocolHashedVersion ver = ProtocolHashedVersion.newBuilder(delta.getHashedVersion()).setHistoryHash(ByteString.copyFromUtf8(hash)).build();
                        new_delta.setHashedVersion(ver);
                    } else {
                        ProtocolHashedVersion ver = ProtocolHashedVersion.newBuilder().setVersion(hashedVersion.getVersion()).setHistoryHash(ByteString.copyFrom(hashedVersion.getHistoryHash())).build();
                        new_delta.setHashedVersion(ver);
                    }
                    waveletProvider.submitRequest(name, new_delta.build(), new WaveletProvider.SubmitRequestListener() {

                        @Override
                        public void onSuccess(int operationsApplied, HashedVersion hashedVersionAfterApplication, long applicationTimestamp) {
                            hashedVersion = hashedVersionAfterApplication;
                        }

                        @Override
                        public void onFailure(String errorMessage) {
                            error.write(errorMessage);
                        }
                    });
                    if (error.getBuffer().length() != 0)
                        break;
                }
            }
            response.setStatus(HttpServletResponse.SC_OK);
            response.getOutputStream().write((skip?"skipped":"imported").getBytes());
        } catch (JSONException ex) {
            Logger.getLogger(ImportServlet.class.getName()).log(Level.SEVERE, null, ex);
            throw new IOException(ex);
        }
        if (error.getBuffer().length() != 0)
            throw new IOException(error.getBuffer().toString());
    }

    private String convertDomains(String participant, String domain) {
        int index = participant.indexOf('@');
        if (index != -1) {
            if (participant.endsWith("@a.gwave.com") && lastParticipant != null)
                participant = lastParticipant;
            else
                participant = participant.substring(0, index+1) + domain;
        }
        lastParticipant = participant;
        return participant;
    }

    private static String readToString(Reader reader) throws FileNotFoundException, IOException {
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
