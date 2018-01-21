package exps.codepattern.code.ir.statement;

import exps.codepattern.code.cfg.basiccfg.BasicCFGRegularBlock;
import exps.codepattern.code.ir.IRExpression;
import exps.codepattern.code.mining.MiningNode;

import java.util.stream.Stream;

public class IRAssignment extends IRStatement {
	private IRExpression source;

	public IRAssignment(IRExpression source, IRExpression.IRAbstractVariable target) {
		addUse(source);
		addDef(target);

		this.source = source;
		this.target = target;
	}

	@Override
	public String toString() {
		return String.format("%s = %s", target, source);
	}

	@Override
	public BasicCFGRegularBlock buildCFG(BasicCFGRegularBlock block) {
		block.addNode(this);
		return block;
	}

	@Override
	public Stream<IRExpression> getUses(ExpressionFilter builder) {
		return builder.add(source).build();
	}

	@Override
	public MiningNode toMiningNode() {
		return MiningNode.ASSIGNMENT;
	}

}
