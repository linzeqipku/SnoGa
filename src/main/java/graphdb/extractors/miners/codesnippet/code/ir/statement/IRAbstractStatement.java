package graphdb.extractors.miners.codesnippet.code.ir.statement;

import graphdb.extractors.miners.codesnippet.code.cfg.basiccfg.BasicCFGRegularBlock;
import graphdb.extractors.miners.codesnippet.code.ir.IRExpression;
import graphdb.extractors.miners.codesnippet.code.ir.IRExpression.IRAbstractVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public interface IRAbstractStatement {
	BasicCFGRegularBlock buildCFG(BasicCFGRegularBlock block);

	abstract class ExpressionFilter {
		private Stream.Builder<IRExpression> builder = Stream.builder();
		private List<Stream<IRExpression>> streams = new ArrayList<>();

		protected abstract boolean filter(IRExpression expression);

		final ExpressionFilter add(IRExpression expression) {
			if (filter(expression)) builder.add(expression);
			return this;
		}

		final ExpressionFilter addAll(IRExpression[] expressions){
			if (expressions == null) return this;
			streams.add(Stream.of(expressions).filter(this::filter));
			return this;
		}

		final Stream<IRExpression> build(){
			Stream<IRExpression> result = builder.build();
			return streams.stream().reduce(result, Stream::concat);
		}
	}

	class VariableFilter extends ExpressionFilter {
		public boolean filter(IRExpression expression) {
			return expression != null && expression instanceof IRAbstractVariable;
		}
	}

	class PreDefinedFilter extends ExpressionFilter {
		public boolean filter(IRExpression expression) {
			return expression != null && !(expression instanceof IRAbstractVariable);
		}
	}
}
