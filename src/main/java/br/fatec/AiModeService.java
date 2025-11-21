package br.fatec;

import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class AiModeService {

    private final AtomicBoolean structuredEnabled = new AtomicBoolean(false);

    public AiModeService(@ConfigProperty(name = "tenebra.ai.structured", defaultValue = "false") boolean initialValue) {
        structuredEnabled.set(initialValue);
    }

    public boolean isStructuredEnabled() {
        return structuredEnabled.get();
    }

    public boolean setStructuredEnabled(boolean enabled) {
        structuredEnabled.set(enabled);
        return enabled;
    }
}

