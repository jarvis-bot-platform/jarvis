package fr.zelus.jarvis.plugins.log.module.action;

import fr.inria.atlanmod.commons.log.Level;
import fr.zelus.jarvis.plugins.log.module.LogModule;
import fr.zelus.jarvis.core.session.JarvisContext;

/**
 * A {@link LogAction} that logs the provided message as an info.
 */
public class LogInfo extends LogAction {

    /**
     * Constructs a new {@link LogInfo} action from the provided {@code containingModule}, {@code context}, and {@code
     * message}.
     *
     * @param containingModule the {@link LogModule} containing this action
     * @param context          the {@link JarvisContext} associated to this action
     * @param message          the message to log
     * @throws NullPointerException if the provided {@code containingModule}, {@code context}, or {@code message} is
     *                              {@code null}
     */
    public LogInfo(LogModule containingModule, JarvisContext context, String message) {
        super(containingModule, context, message, Level.INFO);
    }

}