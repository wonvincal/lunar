package com.lunar.strategy.statemachine;

public class ConcreteState extends State {
	public interface BeginStateRunnable {
		void run(final int prevState, final int transitionId) throws Exception;
	}
	
	final private BeginStateRunnable m_beginState;
	
	static final private BeginStateRunnable s_defaultBeginState = new BeginStateRunnable() {
		@Override
		public void run(final int prevState, final int transitionId) throws Exception {
		}
	};
	
	public ConcreteState(final int id) {
		this(id, s_defaultBeginState);
	}

	public ConcreteState(final int id, final BeginStateRunnable beginState) {
		super(id);
		m_beginState = beginState;
	}

	@Override
	public void beginState(final int prevState, final int transitionId) throws Exception {
		m_beginState.run(prevState, transitionId);
	}
	
}
