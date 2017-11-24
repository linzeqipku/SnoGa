package graphdb.extractors.miners.codesnippet.code.cfg.basiccfg;

import com.google.common.collect.ImmutableSet;
import graphdb.extractors.miners.codesnippet.code.cfg.CFGBlock;

import java.util.HashSet;
import java.util.Set;

/**
 * 常规控制流图基本块的抽象类
 *
 * @author huacy
 */
public abstract class AbstractBasicCFGBlock implements CFGBlock {
	boolean reachable = false;
	Set<AbstractBasicCFGBlock> prevs = new HashSet<>();
	private BasicCFG cfg;
	private int id;

	AbstractBasicCFGBlock(BasicCFG cfg, int id) {
		this.cfg = cfg;
		this.id = id;
	}

	public BasicCFG getCFG() {
		return cfg;
	}

	int getID() {
		return id;
	}

	protected abstract void visit();

	public abstract void insertPhi(CFGVariableImpl exp);

	void addPrev(AbstractBasicCFGBlock prev) {
		prevs.add(prev);
	}

	public int getPrevIndex(AbstractBasicCFGBlock prev) {
		int index = 0;
		for (AbstractBasicCFGBlock p : prevs) {
			if (p == prev) return index;
			++index;
		}
		throw new RuntimeException(String.format("%s is not a prev of %s", prev, this));
	}

	public void checkPrev() {
		prevs.removeIf(p -> !cfg.getBlocks().contains(p));
	}

	@Override
	public ImmutableSet<AbstractBasicCFGBlock> getPrevs() {
		return ImmutableSet.copyOf(prevs);
	}

	@Override
	public abstract ImmutableSet<AbstractBasicCFGBlock> getNexts();

}
