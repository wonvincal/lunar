package com.lunar.strategy.scoreboard;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StrategySwitchController implements ScoreBoardUpdateHandler {
    static final Logger LOG = LogManager.getLogger(StrategySwitchController.class);

    public interface SwitchControlHandler {
        public void switchOnStrategy(final long securitySid);
        public void switchOffStrategy(final long securitySid);
        public void onErrorControlling(final long securitySid);
    }
    
    public static final SwitchControlHandler NULL_SWITCH_CONTROL_HANDLER = new SwitchControlHandler() {
        @Override
        public void switchOnStrategy(long securitySid) {
        }

        @Override
        public void switchOffStrategy(long securitySid) {
        }

        @Override
        public void onErrorControlling(long securitySid) {
        }
    };
    
    static final private int MAX_ATTEMPT = 5;    
    final private ScoreBoardSecurityInfo security;
    final private ScoreBoard scoreBoard;
    final private SwitchControlHandler controlHandler;
    private boolean isAutoControlEnabled;
    private boolean isStrategySwitchedOn;
    private int attemptCount;
    
    public StrategySwitchController(final ScoreBoardSecurityInfo security, final SwitchControlHandler handler) {
        this.security = security;
        this.scoreBoard = security.scoreBoard();
        this.controlHandler = handler;
        this.isAutoControlEnabled = false;
        this.isStrategySwitchedOn = true;
        this.attemptCount = 0;
        this.security.strategySwitchController(this);
        this.security.registerScoreBoardUpdateHandler(this);
    }

    public void enableAutoControl() {
        if (!this.isAutoControlEnabled) {
            LOG.info("Scoreboard auto-control turned on for secCode {}", security.code());
            this.isAutoControlEnabled = true;
            controlStrategy();
        }
    }

    public void disableAutoControl() {
        LOG.info("Scoreboard auto-control turned off for secCode {}", security.code());
        this.isAutoControlEnabled = false;
        this.attemptCount = 0;
    }
    
    public void updateStrategyState(final boolean isStrategySwitchedOn) {
        LOG.info("Strategy state updated for secCode {}, isStrategySwitchedOn {}", security.code(), isStrategySwitchedOn);
        this.isStrategySwitchedOn = isStrategySwitchedOn;
        controlStrategy();
    }
    
    @Override
    public void onScoreBoardUpdated(long nanoOfDay, ScoreBoard scoreBoard) {
        controlStrategy();
    }

    private void controlStrategy() {
        if (isAutoControlEnabled) {
            if (scoreBoard.getScore() >= scoreBoard.getScoreThreshold()) {
                if (!isStrategySwitchedOn) {
                    if (attemptCount++ <= MAX_ATTEMPT) {
                        LOG.info("Switching on strategy for secCode {}", security.code());
                        controlHandler.switchOnStrategy(security.sid());
                    }
                    else {
                        LOG.error("Error controlling strategy for secCode {}", security.code());
                        controlHandler.onErrorControlling(security.sid());
                    }
                }
                else {
                    attemptCount = 0;
                }
            }
            else {
                if (isStrategySwitchedOn) {
                    if (attemptCount++ <= MAX_ATTEMPT) {
                        LOG.info("Switching off strategy for secCode {}...", security.code());
                        controlHandler.switchOffStrategy(security.sid());
                    }
                    else {
                        LOG.error("Error controlling strategy for secCode {}...", security.code());
                        controlHandler.onErrorControlling(security.sid());
                    }
                }
                else {
                    attemptCount = 0;
                }
            }
        }
    }

}
