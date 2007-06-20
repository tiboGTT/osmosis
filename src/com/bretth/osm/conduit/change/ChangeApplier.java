package com.bretth.osm.conduit.change;

import com.bretth.osm.conduit.ConduitRuntimeException;
import com.bretth.osm.conduit.change.impl.ChangeContainer;
import com.bretth.osm.conduit.change.impl.DataPostbox;
import com.bretth.osm.conduit.change.impl.ElementContainer;
import com.bretth.osm.conduit.change.impl.NodeContainer;
import com.bretth.osm.conduit.change.impl.SegmentContainer;
import com.bretth.osm.conduit.change.impl.WayContainer;
import com.bretth.osm.conduit.data.Node;
import com.bretth.osm.conduit.data.Segment;
import com.bretth.osm.conduit.data.Way;
import com.bretth.osm.conduit.sort.ElementByTypeThenIdComparator;
import com.bretth.osm.conduit.task.ChangeAction;
import com.bretth.osm.conduit.task.ChangeSink;
import com.bretth.osm.conduit.task.Sink;
import com.bretth.osm.conduit.task.MultiSinkMultiChangeSinkRunnableSource;


/**
 * Applies a change set to an input source and produces an updated data set.
 * 
 * @author Brett Henderson
 */
public class ChangeApplier implements MultiSinkMultiChangeSinkRunnableSource {
	
	private Sink sink;
	private DataPostbox<ElementContainer> basePostbox;
	private DataPostbox<ChangeContainer> changePostbox;
	
	
	/**
	 * Creates a new instance.
	 * 
	 * @param inputBufferCapacity
	 *            The size of the buffers to use for input sources.
	 */
	public ChangeApplier(int inputBufferCapacity) {
		basePostbox = new DataPostbox<ElementContainer>(inputBufferCapacity);
		changePostbox = new DataPostbox<ChangeContainer>(inputBufferCapacity);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public Sink getSink(int instance) {
		final DataPostbox<ElementContainer> destinationPostbox = basePostbox;
		
		if (instance != 0) {
			throw new ConduitRuntimeException("Sink instance " + instance
					+ " is not valid.");
		}
		
		return new Sink() {
			private DataPostbox<ElementContainer> postbox = destinationPostbox;
			public void processNode(Node node) {
				postbox.put(new NodeContainer(node));
			}
			public void processSegment(Segment segment) {
				postbox.put(new SegmentContainer(segment));
			}
			public void processWay(Way way) {
				postbox.put(new WayContainer(way));
			}
			public void complete() {
				postbox.complete();
			}
			public void release() {
				postbox.release();
			}
		};
	}


	/**
	 * This implementation always returns 1.
	 * 
	 * @return 1
	 */
	public int getSinkCount() {
		return 1;
	}


	/**
	 * {@inheritDoc}
	 */
	public ChangeSink getChangeSink(int instance) {
		final DataPostbox<ChangeContainer> destinationPostbox = changePostbox;
		
		if (instance != 0) {
			throw new ConduitRuntimeException("Change sink instance " + instance
					+ " is not valid.");
		}
		
		return new ChangeSink() {
			private DataPostbox<ChangeContainer> postbox = destinationPostbox;
			public void processNode(Node node, ChangeAction action) {
				postbox.put(new ChangeContainer(new NodeContainer(node), action));
			}
			public void processSegment(Segment segment, ChangeAction action) {
				postbox.put(new ChangeContainer(new SegmentContainer(segment), action));
			}
			public void processWay(Way way, ChangeAction action) {
				postbox.put(new ChangeContainer(new WayContainer(way), action));
			}
			public void complete() {
				postbox.complete();
			}
			public void release() {
				postbox.release();
			}
		};
	}


	/**
	 * This implementation always returns 1.
	 * 
	 * @return 1
	 */
	public int getChangeSinkCount() {
		return 1;
	}


	/**
	 * {@inheritDoc}
	 */
	public void setSink(Sink sink) {
		this.sink = sink;
	}


	/**
	 * Processes the input sources and sends the updated data stream to the
	 * sink.
	 */
	public void run() {
		boolean completed = false;
		
		try {
			ElementByTypeThenIdComparator comparator;
			ElementContainer base = null;
			ChangeContainer change = null;
			
			// Create a comparator for comparing two elements by type and identifier.
			comparator = new ElementByTypeThenIdComparator();
			
			// We continue in the comparison loop while both sources still have data.
			while ((base != null || basePostbox.hasNext()) && (change != null || changePostbox.hasNext())) {
				int comparisonResult;
				
				// Get the next input data where required.
				if (base == null) {
					base = basePostbox.getNext();
				}
				if (change == null) {
					change = changePostbox.getNext();
				}
				
				// Compare the two sources.
				comparisonResult = comparator.compare(base.getElement(), change.getElement().getElement());
				
				if (comparisonResult < 0) {
					// The base element doesn't exist on the change source therefore we simply pass it through.
					base.process(sink);
					base = null;
				} else if (comparisonResult > 0) {
					// This element doesn't exist in the "base" source therefore we
					// are expecting an add.
					if (change.getAction().equals(ChangeAction.Create)) {
						change.getElement().process(sink);
						
					} else {
						throw new ConduitRuntimeException(
							"Cannot perform action " + change.getAction() + " on node with id="
							+ change.getElement().getElement().getId()
							+ " because it doesn't exist in the base source."
						);
					}
					
					change = null;
					
				} else {
					// The same element exists in both sources therefore we are
					// expecting a modify or delete.
					if (change.getAction().equals(ChangeAction.Modify)) {
						change.getElement().process(sink);
						
					} else if (change.getAction().equals(ChangeAction.Delete)) {
						// We don't need to do anything for delete.
						
					} else {
						throw new ConduitRuntimeException(
							"Cannot perform action " + change.getAction() + " on node with id="
							+ change.getElement().getElement().getId()
							+ " because it exists in the base source."
						);
					}
					
					base = null;
					change = null;
				}
			}
			
			// Any remaining "base" elements are unmodified.
			while (base != null || basePostbox.hasNext()) {
				if (base == null) {
					base = basePostbox.getNext();
				}
				base.process(sink);
				base = null;
			}
			// Any remaining "change" elements must be creates.
			while (change != null || changePostbox.hasNext()) {
				if (change == null) {
					change = changePostbox.getNext();
				}
				// This element doesn't exist in the "base" source therefore we
				// are expecting an add.
				if (change.getAction().equals(ChangeAction.Create)) {
					change.getElement().process(sink);
					
				} else {
					throw new ConduitRuntimeException(
						"Cannot perform action " + change.getAction() + " on node with id="
						+ change.getElement().getElement().getId()
						+ " because it doesn't exist in the base source."
					);
				}
				
				change = null;
			}
			
			sink.complete();
			completed = true;
			
		} finally {
			if (!completed) {
				basePostbox.setOutputError();
				changePostbox.setOutputError();
			}
			
			sink.release();
		}
	}
}
