package br.fatec;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.awt.Desktop;
import java.net.URI;
import java.io.IOException;

import org.jboss.logging.Logger;

@ApplicationScoped
public class BrowserLauncher {
    private static final Logger LOG = Logger.getLogger(BrowserLauncher.class);

    @ConfigProperty(name = "tenebra.browser.auto-open", defaultValue = "true")
    boolean autoOpen;

    void onStart(@Observes StartupEvent ev) {
        if (!autoOpen) {
            LOG.info("Abertura automática de navegador desativada (tenebra.browser.auto-open=false).");
            return;
        }
        String url = "http://localhost:8080";
        LOG.info("Tentando abrir o navegador em: " + url);

        try {
            //Tentativa 1: O modo Java padrão (que você estava usando)
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                LOG.info("Navegador aberto via java.awt.Desktop.");
            } else {
                //Tentativa 2: O 'Plano B' se o Desktop não for suportado (ex: modo headless)
                LOG.warn("java.awt.Desktop não suportado. Tentando 'Plano B' via Runtime exec...");
                openBrowserManually(url);
            }
        } catch (Exception e) {
            LOG.error("Falha ao abrir navegador via java.awt.Desktop. Tentando 'Plano B'.", e);
            try {
                //Tentativa 3: Se a Tentativa 1 deu exceção
                openBrowserManually(url);
            } catch (Exception e2) {
                LOG.error("Falha total ao abrir o navegador. Acesse manualmente: " + url, e2);
            }
        }
    }

    /**
     * Tenta abrir o navegador usando comandos nativos do sistema operacional.
     * Isso funciona mesmo em ambientes 'headless' onde o java.awt.Desktop falha.
     */
    private void openBrowserManually(String url) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();

        ProcessBuilder builder;
        if (os.contains("win")) {
            builder = new ProcessBuilder("cmd", "/c", "start", url);
            LOG.info("Executado comando 'start' do Windows.");
        } else if (os.contains("mac")) {
            builder = new ProcessBuilder("open", url);
            LOG.info("Executado comando 'open' do macOS.");
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            builder = new ProcessBuilder("xdg-open", url);
            LOG.info("Executado comando 'xdg-open' do Linux.");
        } else {
            LOG.error("Sistema operacional não suportado para abertura automática do navegador. Acesse manualmente: " + url);
            return;
        }

        builder.inheritIO();
        builder.start();
    }
}