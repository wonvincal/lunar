package com.lunar.strategy.scoreboard;

import java.util.Collection;

import org.agrona.MutableDirectBuffer;

import com.lunar.core.SbeEncodable;
import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.GenericFieldType;
import com.lunar.message.io.sbe.ScoreBoardSchemaSbeEncoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sender.ScoreBoardSender;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class ScoreBoardSchema implements SbeEncodable {
    public class Field {
        private int fieldId;
        private GenericFieldType fieldType;
        
        public Field(final int fieldId, final GenericFieldType fieldType) {
            this.fieldId = fieldId;
            this.fieldType = fieldType;
        }
        
        public int getFieldId() {
            return fieldId;
        }
        public GenericFieldType getFieldType() {
            return fieldType;
        }
    }
    
    static private int ID_COUNTER = 0;
    static public final int NUM_PUNTER_TRADES_ID = ++ID_COUNTER;
    static public final int NUM_PUNTER_WINS_ID = ++ID_COUNTER;
    static public final int NUM_PUNTER_LOSSES_ID = ++ID_COUNTER;
    static public final int SKIPPED_ID_44 = ++ID_COUNTER;
    static public final int SKIPPED_ID_39 = ++ID_COUNTER;
    static public final int SKIPPED_ID_8 = ++ID_COUNTER;
    static public final int SKIPPED_ID_9 = ++ID_COUNTER;
    static public final int SKIPPED_ID_10 = ++ID_COUNTER;
    static public final int SKIPPED_ID_11 = ++ID_COUNTER;
    static public final int MAX_PROFITABLE_QUANTITY_ID = ++ID_COUNTER;
    static public final int MODE_PROFITABLE_QUANTITY_ID = ++ID_COUNTER;
    static public final int SKIPPED_ID_40 = ++ID_COUNTER;
    static public final int ISSUER_SMOOTHING_ID = ++ID_COUNTER;
    static public final int PREV_DAY_OUTSTANDING_PERCENT_ID = ++ID_COUNTER;
    static public final int PREV_DAY_OUTSTANDING_PERCENT_CHANGE_ID = ++ID_COUNTER;
    static public final int SKIPPED_ID_41 = ++ID_COUNTER;
    static public final int NUM_CONSECUTIVE_BREAKEVEN_OR_WINS_ID = ++ID_COUNTER;
    static public final int NUM_CONSECUTIVE_LOSSES_ID = ++ID_COUNTER;
    static public final int SKIPPED_ID_42 = ++ID_COUNTER;
    static public final int SKIPPED_ID_12 = ++ID_COUNTER;
    static public final int NUM_PUNTER_TRIGGERS_ID = ++ID_COUNTER;
    static public final int TICK_SENSITIVITY_ID = ++ID_COUNTER;
    static public final int SKIPPED_ID_13 = ++ID_COUNTER;
    static public final int TWA_SPREAD_ID = ++ID_COUNTER;
    static public final int SKIPPED_ID_14 = ++ID_COUNTER;
    static public final int SKIPPED_ID_43 = ++ID_COUNTER;
    static public final int SKIPPED_ID_15 = ++ID_COUNTER;
    static public final int MODE_SPREAD_ID = ++ID_COUNTER;
    static public final int SKIPPED_ID_16 = ++ID_COUNTER;
    static public final int OUR_SCORE_ID = ++ID_COUNTER;
    //static public final int OUR_PNL_TICKS_ID = ++ID_COUNTER;
    static public final int SKIPPED_ID_4 = ++ID_COUNTER;
    static public final int OUR_PNL_TICKS_WITH_PUNTERS_ID = ++ID_COUNTER;
    //static public final int NUM_OF_OUR_WINS_ID = ++ID_COUNTER;
    static public final int SKIPPED_ID_5 = ++ID_COUNTER;
    static public final int NUM_OF_OUR_LOSSES_ID = ++ID_COUNTER;
    static public final int NUM_OF_OUR_BREAKEVENS_ID = ++ID_COUNTER;
    static public final int NUM_OF_OUR_WINS_WITH_PUNTERS_ID = ++ID_COUNTER;
    static public final int NUM_PUNTER_BREAKEVENS_ID = ++ID_COUNTER;
    //static public final int NUM_VOL_DOWNS_IN_POSITION_ID = ++ID_COUNTER;
    static public final int SKIPPED_ID_1 = ++ID_COUNTER;
    //static public final int NUM_AUTO_VOL_DOWNS_IN_POSITION_ID = ++ID_COUNTER;
    static public final int SKIPPED_ID_2 = ++ID_COUNTER;    
    //static public final int ISSUER_RESPONSE_SCORE_ID = ++ID_COUNTER;
    static public final int SKIPPED_ID_6 = ++ID_COUNTER;
    //static public final int OVERALL_SCORE_ID = ++ID_COUNTER;
    static public final int SKIPPED_ID_7 = ++ID_COUNTER;
    //static public final int RESPONSE_TYPE_ID = ++ID_COUNTER;
    static public final int SKIPPED_ID_3 = ++ID_COUNTER;
    static public final int PREV_DAY_OUTSTANDING_ID = ++ID_COUNTER;
    static public final int IVOL_ID = ++ID_COUNTER;
    static public final int PREV_DAY_IVOL_ID = ++ID_COUNTER;
    static public final int SKIPPED_ID_17 = ++ID_COUNTER;
    static public final int SKIPPED_ID_18 = ++ID_COUNTER;
    static public final int SKIPPED_ID_19 = ++ID_COUNTER;
    static public final int NORMALIZED_MODE_SPREAD_ID = ++ID_COUNTER;
    static public final int NORMALIZED_TWA_SPREAD_ID = ++ID_COUNTER;
    static public final int SKIPPED_ID_20 = ++ID_COUNTER;
    static public final int SKIPPED_ID_21 = ++ID_COUNTER;
    static public final int SKIPPED_ID_22 = ++ID_COUNTER;
    static public final int SKIPPED_ID_23 = ++ID_COUNTER;
    static public final int SKIPPED_ID_24 = ++ID_COUNTER;
    static public final int SKIPPED_ID_25 = ++ID_COUNTER;
    static public final int SKIPPED_ID_26 = ++ID_COUNTER;
    static public final int LAST_TRIGGER_TOTAL_BUY_QUANTITY_ID = ++ID_COUNTER;    
    static public final int LAST_TRIGGER_TOTAL_TRADES_ID = ++ID_COUNTER;    
    static public final int NUM_DROP_VOLS_ID = ++ID_COUNTER;
    static public final int NUM_AUTO_DROP_VOLS_ON_BUY_ID = ++ID_COUNTER;
    static public final int NUM_AUTO_DROP_VOLS_ON_SELL_ID = ++ID_COUNTER;
    static public final int NUM_MANUAL_DROP_VOLS_ON_BUY_ID = ++ID_COUNTER;
    static public final int NUM_MANUAL_DROP_VOLS_ON_SELL_ID = ++ID_COUNTER;
    static public final int NUM_RAISE_VOLS_ID = ++ID_COUNTER;
    static public final int NUM_AUTO_RAISE_VOLS_ON_SELL_ID = ++ID_COUNTER;
    static public final int NUM_MANUAL_RAISE_VOLS_ON_SELL_ID = ++ID_COUNTER;    
    static public final int SKIPPED_ID_27 = ++ID_COUNTER;
    static public final int SKIPPED_ID_28 = ++ID_COUNTER;
    static public final int SKIPPED_ID_29 = ++ID_COUNTER;
    static public final int SKIPPED_ID_30 = ++ID_COUNTER;
    static public final int NUM_PUNTER_BREAKEVEN_OR_WINS_ID = ++ID_COUNTER;
    static public final int AVG_BREAKEVEN_TIME_ID = ++ID_COUNTER;
    static public final int MIN_BREAKEVEN_TIME_ID = ++ID_COUNTER;
    static public final int SKIPPED_ID_31 = ++ID_COUNTER;
    static public final int LAST_TIME_BREAKEVEN_ID = ++ID_COUNTER;
    static public final int AVG_WIN_TIME_ID = ++ID_COUNTER;
    static public final int SKIPPED_ID_32 = ++ID_COUNTER;
    static public final int SKIPPED_ID_37 = ++ID_COUNTER;
    static public final int LAST_TIME_WIN_ID = ++ID_COUNTER;
    static public final int SKIPPED_ID_38 = ++ID_COUNTER;
    static public final int AVG_SMOOTHING_ID = ++ID_COUNTER;
    static public final int AVG_SMOOTHING_PER_TICK_ID = ++ID_COUNTER;
    static public final int SKIPPED_ID_33 = ++ID_COUNTER;
    static public final int SKIPPED_ID_34 = ++ID_COUNTER;
    static public final int SKIPPED_ID_35 = ++ID_COUNTER; 
    static public final int SKIPPED_ID_36 = ++ID_COUNTER; 
    static public final int PREV_DAY_NET_SOLD_ID = ++ID_COUNTER;
    static public final int PREV_DAY_NET_VEGA_SOLD_ID = ++ID_COUNTER;
    static public final int BID_IVOL_ID = ++ID_COUNTER;
    static public final int ASK_IVOL_ID = ++ID_COUNTER;
    static public final int SMOOTHING_ID = ++ID_COUNTER;
    static public final int SMOOTHING_PER_TICK_ID = ++ID_COUNTER;
    static public final int LAST_TIME_SMOOTHING_ID = ++ID_COUNTER;
    static public final int RUNNING_NET_SOLD_ID = ++ID_COUNTER;
    static public final int RUNNING_NET_REVENUE_ID = ++ID_COUNTER;
    static public final int RUNNING_NET_PNL_ID = ++ID_COUNTER;    
    static public final int RUNNING_GROSS_SOLD_ID = ++ID_COUNTER;
    static public final int RUNNING_GROSS_REVENUE_ID = ++ID_COUNTER;
    static public final int RUNNING_GROSS_PNL_ID = ++ID_COUNTER;    
    static public final int NUMBER_BIG_TRADES_ID = ++ID_COUNTER;
    static public final int NUMBER_UNCERTAIN_TRADES_ID = ++ID_COUNTER;
    static public final int VOL_PER_TICK_ID = ++ID_COUNTER;
    static public final int OUR_SCORE_WITH_PUNTERS_ID = ++ID_COUNTER;
    static public final int OUR_PREV_SCORE_WITH_PUNTERS_ID = ++ID_COUNTER;
    static public final int OUR_MTM_SCORE_ID = ++ID_COUNTER;
    static public final int OUR_MTM_SCORE_WITH_PUNTERS_ID = ++ID_COUNTER;
    static public final int LAST_TIME_PUNTER_TRIGGER_ID = ++ID_COUNTER;
    static public final int OUR_MTM_THEORETICAL_PENALTY_ID = ++ID_COUNTER;
    static public final int TWA_SPREAD_IN_POSITION_ID = ++ID_COUNTER;
    static public final int NORMALIZED_TWA_SPREAD_IN_POSITION_ID = ++ID_COUNTER;
    static public final int LAST_BREAKEVEN_TIME_ID = ++ID_COUNTER;
    static public final int LAST_WIN_TIME_ID = ++ID_COUNTER;
    
    static public final int MAX_FIELD_ID = ID_COUNTER;
    
    static private final String[] FIELD_DESCRIPTIONS;

    static {
        FIELD_DESCRIPTIONS = new String[MAX_FIELD_ID + 1];
        FIELD_DESCRIPTIONS[OUR_SCORE_ID] = "Our Score";
        FIELD_DESCRIPTIONS[OUR_SCORE_WITH_PUNTERS_ID] = "Our Score With Punter Trades";
        FIELD_DESCRIPTIONS[OUR_PREV_SCORE_WITH_PUNTERS_ID] = "Our Previous Score With Punter Trades";
        FIELD_DESCRIPTIONS[OUR_MTM_SCORE_ID] = "Our Mtm Score";
        FIELD_DESCRIPTIONS[OUR_MTM_SCORE_WITH_PUNTERS_ID] = "Our Mtm Score With Punter Trades";
        FIELD_DESCRIPTIONS[OUR_PNL_TICKS_WITH_PUNTERS_ID] = "Our Pnl Ticks with Punter Trades";
        FIELD_DESCRIPTIONS[NUM_OF_OUR_WINS_WITH_PUNTERS_ID] = "Num Our Wins with Punter Trades";
        FIELD_DESCRIPTIONS[NUM_OF_OUR_BREAKEVENS_ID] = "Num Our Breakevens";
        FIELD_DESCRIPTIONS[NUM_OF_OUR_LOSSES_ID] = "Num Our Losses";
        FIELD_DESCRIPTIONS[OUR_MTM_THEORETICAL_PENALTY_ID] = "Our Mtm Theoretical Ticks Penalty";

        FIELD_DESCRIPTIONS[NUM_PUNTER_TRIGGERS_ID] = "Num Punter Triggers";
        FIELD_DESCRIPTIONS[NUM_PUNTER_TRADES_ID] = "Num Punter Buy Trades";
        FIELD_DESCRIPTIONS[NUM_PUNTER_WINS_ID] = "Num Punter Wins";
        FIELD_DESCRIPTIONS[NUM_PUNTER_BREAKEVENS_ID] = "Num Punter Breakevens";
        FIELD_DESCRIPTIONS[NUM_PUNTER_BREAKEVEN_OR_WINS_ID] = "Num Punter BE/Wins";
        FIELD_DESCRIPTIONS[NUM_PUNTER_LOSSES_ID] = "Num Punter Losses";
        
        FIELD_DESCRIPTIONS[NUM_CONSECUTIVE_BREAKEVEN_OR_WINS_ID] = "Consecutive BE/Wins";
        FIELD_DESCRIPTIONS[NUM_CONSECUTIVE_LOSSES_ID] = "Consecutive Losses";
        FIELD_DESCRIPTIONS[LAST_TIME_PUNTER_TRIGGER_ID] = "Last Trigger Time";

        FIELD_DESCRIPTIONS[AVG_BREAKEVEN_TIME_ID] = "Avg Breakeven Duration";
        FIELD_DESCRIPTIONS[MIN_BREAKEVEN_TIME_ID] = "Min Breakeven Duration";
        FIELD_DESCRIPTIONS[LAST_BREAKEVEN_TIME_ID] = "Last Breakeven Duration";
        FIELD_DESCRIPTIONS[LAST_TIME_BREAKEVEN_ID] = "Last Time Breakeven";
        FIELD_DESCRIPTIONS[AVG_WIN_TIME_ID] = "Avg Profit Duration";
        FIELD_DESCRIPTIONS[LAST_WIN_TIME_ID] = "Last Profit Duration";
        FIELD_DESCRIPTIONS[LAST_TIME_WIN_ID] = "Last Time Profit";
        
        FIELD_DESCRIPTIONS[LAST_TRIGGER_TOTAL_BUY_QUANTITY_ID] = "Total Qty for Last Buy Trigger";    
        FIELD_DESCRIPTIONS[LAST_TRIGGER_TOTAL_TRADES_ID] = "Total Trades for Last Buy Trigger"; 

        FIELD_DESCRIPTIONS[MAX_PROFITABLE_QUANTITY_ID] = "Max Profitable Quantity";
        FIELD_DESCRIPTIONS[MODE_PROFITABLE_QUANTITY_ID] = "Mode Profitable Quantity";

        FIELD_DESCRIPTIONS[MODE_SPREAD_ID] = "Mode Spread";
        FIELD_DESCRIPTIONS[NORMALIZED_MODE_SPREAD_ID] = "Normalized Mode Spread";
        
        FIELD_DESCRIPTIONS[TWA_SPREAD_ID] = "Twa Spread";
        FIELD_DESCRIPTIONS[NORMALIZED_TWA_SPREAD_ID] = "Normalized Twa Spread";
        FIELD_DESCRIPTIONS[TWA_SPREAD_IN_POSITION_ID] = "Twa Spread in Position";
        FIELD_DESCRIPTIONS[NORMALIZED_TWA_SPREAD_IN_POSITION_ID] = "Normalized Twa Spread in Position";

        FIELD_DESCRIPTIONS[ISSUER_SMOOTHING_ID] = "Issuer Smoothing";
        FIELD_DESCRIPTIONS[TICK_SENSITIVITY_ID] = "Tick Sensitivity";

        FIELD_DESCRIPTIONS[PREV_DAY_OUTSTANDING_ID] = "Previous Day OS";
        FIELD_DESCRIPTIONS[PREV_DAY_OUTSTANDING_PERCENT_ID] = "Previous Day OS %";
        FIELD_DESCRIPTIONS[PREV_DAY_NET_SOLD_ID] = "Prev Day Net Sold";
        FIELD_DESCRIPTIONS[PREV_DAY_NET_VEGA_SOLD_ID] = "Prev Day Net Vega Sold";
        FIELD_DESCRIPTIONS[PREV_DAY_OUTSTANDING_PERCENT_CHANGE_ID] = "Previous Day OS % Change";

        FIELD_DESCRIPTIONS[BID_IVOL_ID] = "Bid IVol";
        FIELD_DESCRIPTIONS[ASK_IVOL_ID] = "Ask IVol";
        FIELD_DESCRIPTIONS[IVOL_ID] = "IVol";
        FIELD_DESCRIPTIONS[PREV_DAY_IVOL_ID] = "Previous Day IVol";
        FIELD_DESCRIPTIONS[VOL_PER_TICK_ID] = "Vol Per Tick";

        FIELD_DESCRIPTIONS[NUM_DROP_VOLS_ID] = "Num Drop Vols";
        FIELD_DESCRIPTIONS[NUM_AUTO_DROP_VOLS_ON_BUY_ID] = "Num Auto Drop Vols on Buy";
        FIELD_DESCRIPTIONS[NUM_AUTO_DROP_VOLS_ON_SELL_ID] = "Num Auto Drop Vols on Sell";
        FIELD_DESCRIPTIONS[NUM_MANUAL_DROP_VOLS_ON_BUY_ID] = "Num Manual Drop Vols on Buy";
        FIELD_DESCRIPTIONS[NUM_MANUAL_DROP_VOLS_ON_SELL_ID] = "Num Manual Drop Vols on Sell";
        FIELD_DESCRIPTIONS[NUM_RAISE_VOLS_ID] = "Num Raise Vols";
        FIELD_DESCRIPTIONS[NUM_AUTO_RAISE_VOLS_ON_SELL_ID] = "Num Auto Raise Vols on Sell";
        FIELD_DESCRIPTIONS[NUM_MANUAL_RAISE_VOLS_ON_SELL_ID] = "Num Manual Raise Vols on Sell";
        
        FIELD_DESCRIPTIONS[AVG_SMOOTHING_ID] = "Avg Smoothing";
        FIELD_DESCRIPTIONS[AVG_SMOOTHING_PER_TICK_ID] = "Avg Smoothing Per Tick";

        FIELD_DESCRIPTIONS[RUNNING_NET_SOLD_ID] = "Running Net Sold";   
        FIELD_DESCRIPTIONS[RUNNING_NET_REVENUE_ID] = "Running Net Revenue";   
        FIELD_DESCRIPTIONS[RUNNING_NET_PNL_ID] = "Running Net M2M Pnl";  
        FIELD_DESCRIPTIONS[RUNNING_GROSS_SOLD_ID] = "Running Gross Sold";   
        FIELD_DESCRIPTIONS[RUNNING_GROSS_REVENUE_ID] = "Running Gross Revenue";   
        FIELD_DESCRIPTIONS[RUNNING_GROSS_PNL_ID] = "Running Gross M2M Pnl";
        FIELD_DESCRIPTIONS[NUMBER_BIG_TRADES_ID] = "Num Big Trades";   
        FIELD_DESCRIPTIONS[NUMBER_UNCERTAIN_TRADES_ID] = "Num Uncertain Trades";

    }
    
    private ObjectArrayList<Field> fields;
    
    static public ScoreBoardSchema of() {
        return new ScoreBoardSchema();
    }
    
    public ScoreBoardSchema() {
        populateSchema();
    }
    
    public static String getFieldDescription(final int fieldId) {
        return FIELD_DESCRIPTIONS[fieldId];
    }
    
    private void populateSchema() {
        fields = ObjectArrayList.wrap(new Field[MAX_FIELD_ID + 1]);
        fields.size(0);
        addField(OUR_SCORE_ID, GenericFieldType.LONG);        
        addField(OUR_SCORE_WITH_PUNTERS_ID, GenericFieldType.LONG);
        addField(OUR_PREV_SCORE_WITH_PUNTERS_ID, GenericFieldType.LONG);
        addField(OUR_MTM_SCORE_ID, GenericFieldType.LONG);        
        addField(OUR_MTM_SCORE_WITH_PUNTERS_ID, GenericFieldType.LONG);
        addField(OUR_PNL_TICKS_WITH_PUNTERS_ID, GenericFieldType.LONG);
        addField(NUM_OF_OUR_WINS_WITH_PUNTERS_ID, GenericFieldType.LONG);
        addField(NUM_OF_OUR_BREAKEVENS_ID, GenericFieldType.LONG);
        addField(NUM_OF_OUR_LOSSES_ID, GenericFieldType.LONG);
        addField(OUR_MTM_THEORETICAL_PENALTY_ID, GenericFieldType.LONG);

        addField(NUM_PUNTER_TRADES_ID, GenericFieldType.LONG);
        addField(NUM_PUNTER_TRIGGERS_ID, GenericFieldType.LONG);
        addField(NUM_PUNTER_WINS_ID, GenericFieldType.LONG);
        addField(NUM_PUNTER_BREAKEVENS_ID, GenericFieldType.LONG);
        addField(NUM_PUNTER_BREAKEVEN_OR_WINS_ID, GenericFieldType.LONG);
        addField(NUM_PUNTER_LOSSES_ID, GenericFieldType.LONG);
        
        addField(NUM_CONSECUTIVE_BREAKEVEN_OR_WINS_ID, GenericFieldType.LONG);
        addField(NUM_CONSECUTIVE_LOSSES_ID, GenericFieldType.LONG);
        addField(LAST_TIME_PUNTER_TRIGGER_ID, GenericFieldType.NANOSECOND);
        
        addField(AVG_BREAKEVEN_TIME_ID, GenericFieldType.NANOSECOND); 
        addField(MIN_BREAKEVEN_TIME_ID, GenericFieldType.NANOSECOND); 
        addField(LAST_BREAKEVEN_TIME_ID, GenericFieldType.NANOSECOND);
        addField(LAST_TIME_BREAKEVEN_ID, GenericFieldType.NANOSECOND); 
        addField(AVG_WIN_TIME_ID, GenericFieldType.NANOSECOND); 
        addField(LAST_WIN_TIME_ID, GenericFieldType.NANOSECOND);
        addField(LAST_TIME_WIN_ID, GenericFieldType.NANOSECOND);

        addField(LAST_TRIGGER_TOTAL_BUY_QUANTITY_ID, GenericFieldType.LONG);    
        addField(LAST_TRIGGER_TOTAL_TRADES_ID, GenericFieldType.LONG);

        //addField(MAX_PROFITABLE_QUANTITY_ID, GenericFieldType.LONG);
        //addField(MODE_PROFITABLE_QUANTITY_ID, GenericFieldType.LONG);
        
        addField(MODE_SPREAD_ID, GenericFieldType.LONG);
        //addField(NORMALIZED_MODE_SPREAD_ID, GenericFieldType.LONG);
        addField(TWA_SPREAD_ID, GenericFieldType.DECIMAL3);
        addField(NORMALIZED_TWA_SPREAD_ID, GenericFieldType.DECIMAL3);
        addField(TWA_SPREAD_IN_POSITION_ID, GenericFieldType.DECIMAL3);
        addField(NORMALIZED_TWA_SPREAD_IN_POSITION_ID, GenericFieldType.DECIMAL3);

        addField(TICK_SENSITIVITY_ID, GenericFieldType.DECIMAL3);
        addField(ISSUER_SMOOTHING_ID, GenericFieldType.NANOSECOND);

        addField(PREV_DAY_OUTSTANDING_ID, GenericFieldType.LONG);
        addField(PREV_DAY_OUTSTANDING_PERCENT_ID, GenericFieldType.DECIMAL3);
        addField(PREV_DAY_NET_SOLD_ID, GenericFieldType.LONG);
        addField(PREV_DAY_NET_VEGA_SOLD_ID, GenericFieldType.LONG);
        addField(PREV_DAY_OUTSTANDING_PERCENT_CHANGE_ID, GenericFieldType.DECIMAL3);        

        addField(BID_IVOL_ID, GenericFieldType.DECIMAL3);
        addField(ASK_IVOL_ID, GenericFieldType.DECIMAL3);
        addField(IVOL_ID, GenericFieldType.DECIMAL3);
        addField(PREV_DAY_IVOL_ID, GenericFieldType.DECIMAL3);
        addField(VOL_PER_TICK_ID, GenericFieldType.DECIMAL5);

        addField(NUM_DROP_VOLS_ID, GenericFieldType.LONG); 
        addField(NUM_AUTO_DROP_VOLS_ON_BUY_ID, GenericFieldType.LONG); 
        addField(NUM_AUTO_DROP_VOLS_ON_SELL_ID, GenericFieldType.LONG); 
        addField(NUM_MANUAL_DROP_VOLS_ON_BUY_ID, GenericFieldType.LONG); 
        addField(NUM_MANUAL_DROP_VOLS_ON_SELL_ID, GenericFieldType.LONG); 
        addField(NUM_RAISE_VOLS_ID, GenericFieldType.LONG); 
        addField(NUM_AUTO_RAISE_VOLS_ON_SELL_ID, GenericFieldType.LONG); 
        addField(NUM_MANUAL_RAISE_VOLS_ON_SELL_ID, GenericFieldType.LONG);
        
        addField(AVG_SMOOTHING_ID, GenericFieldType.NANOSECOND);
        addField(AVG_SMOOTHING_PER_TICK_ID, GenericFieldType.NANOSECOND);
        
        addField(RUNNING_NET_SOLD_ID, GenericFieldType.LONG);        
        addField(RUNNING_NET_REVENUE_ID, GenericFieldType.DECIMAL3);        
        addField(RUNNING_NET_PNL_ID, GenericFieldType.DECIMAL3);
        addField(RUNNING_GROSS_SOLD_ID, GenericFieldType.LONG);        
        addField(RUNNING_GROSS_REVENUE_ID, GenericFieldType.DECIMAL3);        
        addField(RUNNING_GROSS_PNL_ID, GenericFieldType.DECIMAL3);
        addField(NUMBER_BIG_TRADES_ID, GenericFieldType.LONG);        
        addField(NUMBER_UNCERTAIN_TRADES_ID, GenericFieldType.LONG);
    }
    
    private void addField(final int fieldId, final GenericFieldType fieldType) {
        fields.add(new Field(fieldId, fieldType));
    }
    
    public Collection<Field> fields() {
        return fields;
    }
    
    @Override
    public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
        return ScoreBoardSender.encodeScoreBoardSchemaOnly(buffer, offset, stringBuffer, encoder.scoreBoardSchemaSbeEncoder(), this);
    }

    @Override
    public TemplateType templateType(){
        return TemplateType.SCOREBOARD_SCHEMA;
    }

    @Override
    public short blockLength() {
        return ScoreBoardSchemaSbeEncoder.BLOCK_LENGTH;
    }
    
    @Override
    public int expectedEncodedLength() {
        return ScoreBoardSender.expectedEncodedLength(this);
    }

    @Override
    public int schemaId() {
        return ScoreBoardSchemaSbeEncoder.SCHEMA_ID;
    }

    @Override
    public int schemaVersion() {
        return ScoreBoardSchemaSbeEncoder.SCHEMA_VERSION;
    }
    
}
