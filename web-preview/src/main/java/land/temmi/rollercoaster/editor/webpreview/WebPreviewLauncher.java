package land.temmi.rollercoaster.editor.webpreview;

import com.github.xpenatan.gdx.teavm.backends.web.WebApplication;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;

public final class WebPreviewLauncher {
    private WebPreviewLauncher() { }

    public static void main(String[] args) {
        WebApplicationConfiguration config = new WebApplicationConfiguration();
        // Zero size fills the page, which the editor sizes as an iframe.
        config.width = 0;
        config.height = 0;
        config.useGL30 = true;
        new WebApplication(new WebPreviewApplication(), config);
    }
}
