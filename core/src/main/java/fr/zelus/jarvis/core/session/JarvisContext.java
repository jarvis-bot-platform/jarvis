package fr.zelus.jarvis.core.session;

import fr.inria.atlanmod.commons.log.Log;
import fr.zelus.jarvis.core.JarvisException;
import fr.zelus.jarvis.io.EventProvider;
import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.Configuration;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static fr.inria.atlanmod.commons.Preconditions.checkNotNull;
import static java.util.Objects.nonNull;

/**
 * A variable container bound to a {@link JarvisSession}.
 * <p>
 * This class stores the different variables that can be set during user input processing and accessed by executed
 * {@link fr.zelus.jarvis.core.JarvisAction}. {@link JarvisContext} is used to store:
 * <ul>
 * <li><b>{@link EventProvider} values</b> such as the user name, the channel where the
 * message was received, etc</li>
 * <li><b>Intent recognition values</b>, that are computed by the Intent recognition engine and used to pass
 * information between messages</li>
 * <li><b>Action values</b>, that are returned by {@link fr.zelus.jarvis.core.JarvisAction}s</li>
 * </ul>
 * <p>
 * This class is heavily used by jarvis core component to pass {@link fr.zelus.jarvis.core.JarvisAction} parameters,
 * and replace output message variables by their concrete values.
 */
public class JarvisContext {

    /**
     * The {@link Configuration} key to store maximum time to spend waiting for a context variable (in seconds).
     */
    public static String VARIABLE_TIMEOUT_KEY = "jarvis.context.variable.timeout";

    /**
     * The default amount of time to spend waiting for a context variable (in seconds).
     */
    public static int DEFAULT_VARIABLE_TIMEOUT_VALUE = 2;

    /**
     * The sub-contexts associated to this class.
     * <p>
     * Sub-contexts are used to characterize the variables stored in the global context. As an example, a sub-context
     * <i>slack</i> hold all the variables related to Slack.
     */
    private Map<String, Map<String, Object>> contexts;

    /**
     * The amount of time to spend waiting for a context variable (in seconds).
     * <p>
     * This attribute is equals to {@link #DEFAULT_VARIABLE_TIMEOUT_VALUE} unless a specific value is provided in this
     * class' {@link Configuration} constructor parameter
     *
     * @see #JarvisContext(Configuration)
     */
    private int variableTimeout;

    /**
     * Constructs a new empty {@link JarvisContext}.
     * <p>
     * See {@link #JarvisContext(Configuration)} to construct a {@link JarvisContext} with a given
     * {@link Configuration}.
     *
     * @see #JarvisContext(Configuration)
     */
    public JarvisContext() {
        this(new BaseConfiguration());
    }

    /**
     * Constructs a new empty {@link JarvisContext} with the given {@code configuration}.
     * <p>
     * The provided {@link Configuration} contains information to customize the {@link JarvisContext} behavior, such
     * as the {@code timeout} to retrieve {@link Future} variables.
     *
     * @param configuration the {@link Configuration} parameterizing the {@link JarvisContext}
     * @throws NullPointerException if the provided {@code configuration} is {@code null}
     */
    public JarvisContext(Configuration configuration) {
        checkNotNull(configuration, "Cannot construct a %s from the provided %s: %s", JarvisContext.class
                .getSimpleName(), Configuration.class.getSimpleName(), configuration);
        this.contexts = new HashMap<>();
        if (configuration.containsKey(VARIABLE_TIMEOUT_KEY)) {
            this.variableTimeout = configuration.getInt(VARIABLE_TIMEOUT_KEY);
            Log.info("Setting context variable timeout to {0}s", variableTimeout);
        } else {
            this.variableTimeout = DEFAULT_VARIABLE_TIMEOUT_VALUE;
            Log.info("Using default context variable timeout ({0}s)", DEFAULT_VARIABLE_TIMEOUT_VALUE);
        }
    }

    /**
     * Returns the amount of time the {@link JarvisContext} can spend waiting for a context variable (in seconds).
     * <p>
     * This value can be set in this class' {@link Configuration} constructor parameter.
     *
     * @return the amount of time the {@link JarvisContext} can spend waiting for a context variable (in seconds)
     */
    public int getVariableTimeout() {
        return variableTimeout;
    }

    /**
     * Stores the provided {@code value} in the given {@code context} with the provided {@code key}.
     * <p>
     * As an example, calling setContextValue("slack", "username", "myUsername") sets the variable
     * <i>username</i> with the value <i>myUsername</i> in the <i>slack</i> context.
     * <p>
     * To retrieve all the variables of a given sub-context see {@link #getContextVariables(String)}.
     *
     * @param context the sub-context to store the value in
     * @param key     the sub-context key associated to the value
     * @param value   the value to store
     * @throws NullPointerException if the provided {@code context} or {@code key} is {@code null}
     * @see #getContextVariables(String)
     * @see #getContextValue(String, String)
     */
    public void setContextValue(String context, String key, Object value) {
        checkNotNull(context, "Cannot set the value to the context null");
        checkNotNull(key, "Cannot set the value to the context %s with the key null", context);
        Log.info("Setting context variable {0}.{1} to {2}", context, key, value);
        if (contexts.containsKey(context)) {
            Map<String, Object> contextValues = contexts.get(context);
            contextValues.put(key, value);
        } else {
            Map<String, Object> contextValues = new HashMap<>();
            contextValues.put(key, value);
            contexts.put(context, contextValues);
        }
    }

    /**
     * Returns all the variables stored in the given {@code context}.
     * <p>
     * This method returns an unmodifiable {@link Map} holding the sub-context variables. To retrieve a specific
     * variable from {@link JarvisContext} see {@link #getContextValue(String, String)}.
     *
     * @param context the sub-context to retrieve the variables from
     * @return an unmodifiable {@link Map} holding the sub-context variables
     * @throws NullPointerException if the provided {@code context} is {@code null}
     * @see #getContextValue(String, String)
     */
    public Map<String, Object> getContextVariables(String context) {
        checkNotNull(context, "Cannot retrieve the context variables from the null context");
        if (contexts.containsKey(context)) {
            return Collections.unmodifiableMap(contexts.get(context));
        } else {
            return Collections.emptyMap();
        }
    }

    /**
     * Returns the {@code context} value associated to the provided {@code key}.
     * <p>
     * As an example, calling getContextValue("slack", "username") returns the value of the <i>username</i> variable
     * stored in the <i>slack</i> sub-context.
     * <p>
     * To retrieve all the variables of a given sub-context see {@link #getContextVariables(String)}.
     *
     * @param context the sub-context to retrieve the variable from
     * @param key     the sub-context key associated to the value
     * @return the {@code context} value associated to the provided {@code key}, or {@code null} if the {@code key}
     * does not exist
     * @throws NullPointerException if the provided {@code context} or {@code key} is {@code null}
     * @see #getContextVariables(String)
     */
    public Object getContextValue(String context, String key) {
        checkNotNull(context, "Cannot find the context value from the null context");
        checkNotNull(key, "Cannot find the value of the context %s with the key null", context);
        Map<String, Object> contextVariables = getContextVariables(context);
        if (nonNull(contextVariables)) {
            return contextVariables.get(key);
        } else {
            return null;
        }
    }

    /**
     * Merges the provided {@code other} {@link JarvisContext} into this one.
     * <p>
     * This method adds all the {@code contexts} and {@code values} of the provided {@code other}
     * {@link JarvisContext} to this one, performing a deep copy of the underlying {@link Map} structure, ensuring
     * that future updates on the {@code other} {@link JarvisContext} will not be applied on this one (such as value
     * and context additions/deletions).
     * <p>
     * However, note that the values stored in the context {@link Map}s are not cloned, meaning that
     * {@link fr.zelus.jarvis.core.JarvisAction}s updating existing values will update them for all the merged
     * context (see #129).
     *
     * @param other the {@link JarvisContext} to merge into this one
     * @throws JarvisException if the provided {@link JarvisContext} defines at least one {@code context} with the
     *                         same name as one of the {@code contexts} stored in this {@link JarvisContext}
     */
    public void merge(JarvisContext other) {
        checkNotNull(other, "Cannot merge the provided %s %s", JarvisContext.class.getSimpleName(), other);
        other.getContextMap().forEach((k, v) -> {
            if (this.contexts.containsKey(k)) {
                throw new JarvisException(MessageFormat.format("Cannot merge the provided {0}, duplicated value for " +
                        "context {1}", JarvisContext.class.getSimpleName(), k));
            } else {
                Map<String, Object> variableMap = new HashMap<>();
                v.forEach((k1, v1) -> {
                    /*
                     * v1 is not cloned here, so concrete values are shared between the contexts. This may be an
                     * issue if some actions update existing context variables. In this case we'll need to implement
                     * a deep copy of the variables themselves. (see #129)
                     */
                    variableMap.put(k1, v1);
                });
                this.contexts.put(k, variableMap);
            }
        });
    }

    /**
     * Returns an unmodifiable {@link Map} representing the stored context values.
     *
     * @return an unmodifiable {@link Map} representing the stored context values
     */
    public Map<String, Map<String, Object>> getContextMap() {
        return Collections.unmodifiableMap(contexts);
    }

    /**
     * Replace declared variables from {@code message} by their context values.
     * <p>
     * This method searches for variable patterns in the provided {@code message} and retrieves the corresponding
     * values from the context. Variables accessing a context value should be declared following this template:
     * {@code {$contextName.variableName}}.
     * <p>
     * If a variable cannot be replaced the variable pattern is left unchanged.
     *
     * @param message the message to replace the variables from
     * @return the provided {@code message} with its declared variables replaced by their context values.
     * @throws JarvisException if an error occurred when retrieving a value from a previous action
     */
    public String fillContextValues(String message) {
        checkNotNull(message, "Cannot fill the context values of the null message");
        Log.info("Processing message {0}", message);
        String outMessage = message;
        Matcher m = Pattern.compile("\\{\\$\\S+\\}").matcher(message);
        while (m.find()) {
            String group = m.group();
            Log.info("Found context variable {0}", group);
            /*
             * Cannot be empty.
             */
            String filteredGroup = group.substring(2);
            String[] splitGroup = filteredGroup.split("\\.");
            if (splitGroup.length == 2) {
                Log.info("Looking for context \"{0}\"", splitGroup[0]);
                Map<String, Object> variables = this.getContextVariables(splitGroup[0]);
                if (nonNull(variables)) {
                    String variableIdentifier = splitGroup[1].substring(0, splitGroup[1].length() - 1);
                    Object value = variables.get(variableIdentifier);
                    Log.info("Looking for variable \"{0}\"", variableIdentifier);
                    if (nonNull(value)) {
                        String printedValue = null;
                        if (value instanceof Future) {
                            try {
                                printedValue = ((Future) value).get(variableTimeout, TimeUnit.SECONDS).toString();
                                Log.info("Found value {0} for {1}.{2}", printedValue, splitGroup[0],
                                        variableIdentifier);
                            } catch (InterruptedException | ExecutionException e) {
                                String errorMessage = MessageFormat.format("An error occurred when retrieving the " +
                                        "value of the variable {0}", variableIdentifier);
                                Log.error(errorMessage);
                                throw new JarvisException(e);
                            } catch (TimeoutException e) {
                                /*
                                 * The Future takes too long to compute, return a placeholder (see https://github
                                 * .com/gdaniel/jarvis/wiki/Troubleshooting#my-bot-sends-task-takes-too-long-to
                                 * -compute-messages).
                                 */
                                Log.error("The value for {0}.{1} took too long to complete, stopping it and returning" +
                                        " a placeholder", splitGroup[0], variableIdentifier);
                                ((Future) value).cancel(true);
                                printedValue = "<Task took too long to complete>";
                            } catch (CancellationException e) {
                                Log.error("Cannot retrieve the value for {0}.{1}: the task has been cancelled, " +
                                        "returning a placeholder", splitGroup[0], variableIdentifier);
                                printedValue = "<Task has been cancelled>";
                            }
                        } else {
                            printedValue = value.toString();
                            Log.info("found value {0} for {1}.{2}", printedValue, splitGroup[0],
                                    variableIdentifier);
                        }
                        outMessage = outMessage.replace(group, printedValue);
                    } else {
                        Log.error("The context variable {0} is null", group);
                    }
                } else {
                    Log.error("The context variable {0} does not exist", group);
                }
            } else {
                Log.error("Invalid context variable access: {0}", group);
            }
        }
        return outMessage;
    }
}
