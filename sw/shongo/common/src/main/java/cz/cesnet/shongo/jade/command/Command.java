package cz.cesnet.shongo.jade.command;

import cz.cesnet.shongo.api.CommandException;
import cz.cesnet.shongo.fault.jade.CommandFailureException;
import cz.cesnet.shongo.fault.jade.CommandTimeoutException;
import cz.cesnet.shongo.jade.Agent;
import org.slf4j.LoggerFactory;

/**
 * Represents a command which can be processed on a JADE agent.
 *
 * @author Martin Srom <martin.srom@cesnet.cz>
 */
public abstract class Command
{
    /**
     * How long to wait for command result. Unit: milliseconds
     * <p/>
     * NOTE: some commands, e.g. dialing, may take up to 30 seconds on some devices...
     */
    public static final int COMMAND_TIMEOUT = 33000;

    /**
     * Current command state.
     */
    private State state;

    /**
     * {@link CommandFailureException}.
     */
    private CommandFailureException failure;

    /**
     * Result of the command.
     */
    private Object result;

    /**
     * Constructor.
     */
    public Command()
    {
        state = State.UNKNOWN;
    }

    /**
     * @return name of the command which can be used, e.g., for depicting the command in failures
     */
    public String getName()
    {
        return getClass().getSimpleName();
    }

    /**
     * @return {@link #state}
     */
    public State getState()
    {
        return state;
    }

    /**
     * @return true if command was already processed,
     *         false otherwise
     */
    public boolean isProcessed()
    {
        return state != State.UNKNOWN;
    }

    /**
     * @param state sets the {@link #state}
     */
    public void setState(State state)
    {
        this.state = state;
    }

    /**
     * Sets the {@link #state} as {@link State#FAILED}.
     *
     * @param failure sets the {@link #failure}
     */
    public void setFailed(CommandFailureException failure)
    {
        this.state = State.FAILED;
        this.failure = failure;
        if (this.failure != null) {
            this.failure.setCommand(getName());
        }
    }

    /**
     * @return {@link #failure}
     */
    public CommandFailureException getFailure()
    {
        return failure;
    }

    /**
     * @return {@link #result}
     */
    public Object getResult()
    {
        return result;
    }

    /**
     * @param result sets the {@link #result}
     */
    public void setResult(Object result)
    {
        LoggerFactory.getLogger("test").debug("set result");
        this.result = result;
    }

    /**
     * Process this command on an agent.
     *
     * @param agent agent processing the command
     */
    public abstract void process(Agent agent) throws CommandException;

    /**
     * Wait for the command to be processed
     */
    public void waitForProcessed()
    {
        final int waitingTime = 50;

        // FIXME: use some kind of IPC instead of busy waiting
        int count = COMMAND_TIMEOUT / waitingTime;
        while (!isProcessed() && count > 0) {
            count--;
            try {
                Thread.sleep(waitingTime);
            }
            catch (InterruptedException exception) {
                exception.printStackTrace();
            }
        }
        if (getState() == Command.State.UNKNOWN) {
            setFailed(new CommandTimeoutException());
        }
    }

    /**
     * State of the command.
     */
    public static enum State
    {
        /**
         * Unknown state.
         */
        UNKNOWN,

        /**
         * Command was performed successfully.
         */
        SUCCESSFUL,

        /**
         * Command failed.
         */
        FAILED,
    }
}
