/*
 * Copyright (C) 2018 Tran Le Duy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.duy.ide.javaide.editor.autocomplete.internal;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.duy.ide.code.api.SuggestItem;
import com.duy.ide.editor.internal.suggestion.Editor;
import com.duy.ide.javaide.editor.autocomplete.model.FieldDescription;
import com.duy.ide.javaide.editor.autocomplete.model.MethodDescription;
import com.duy.ide.javaide.editor.autocomplete.parser.IClass;
import com.duy.ide.javaide.editor.autocomplete.parser.JavaDexClassLoader;
import com.duy.ide.javaide.editor.autocomplete.parser.JavaParser;
import com.duy.ide.javaide.utils.DLog;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCSynchronized;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.sun.tools.javac.tree.JCTree.JCBlock;
import static com.sun.tools.javac.tree.JCTree.JCCase;
import static com.sun.tools.javac.tree.JCTree.JCCatch;
import static com.sun.tools.javac.tree.JCTree.JCClassDecl;
import static com.sun.tools.javac.tree.JCTree.JCDoWhileLoop;
import static com.sun.tools.javac.tree.JCTree.JCEnhancedForLoop;
import static com.sun.tools.javac.tree.JCTree.JCErroneous;
import static com.sun.tools.javac.tree.JCTree.JCExpression;
import static com.sun.tools.javac.tree.JCTree.JCExpressionStatement;
import static com.sun.tools.javac.tree.JCTree.JCForLoop;
import static com.sun.tools.javac.tree.JCTree.JCIf;
import static com.sun.tools.javac.tree.JCTree.JCLabeledStatement;
import static com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import static com.sun.tools.javac.tree.JCTree.JCReturn;
import static com.sun.tools.javac.tree.JCTree.JCStatement;
import static com.sun.tools.javac.tree.JCTree.JCSwitch;
import static com.sun.tools.javac.tree.JCTree.JCThrow;
import static com.sun.tools.javac.tree.JCTree.JCTry;
import static com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import static com.sun.tools.javac.tree.JCTree.JCWhileLoop;

/**
 * This class very complex, implement after
 */
public class CompleteExpression extends JavaCompleteMatcherImpl {
    private static final String TAG = "CompleteExpression";
    private final JavaParser mJavaParser;
    private final JavaDexClassLoader mClassLoader;
    private Map<JCTree, Integer> mEndPositions;
    private JCCompilationUnit mAst;
    private int mCursor;
    private Editor mEditor;

    public CompleteExpression(@NonNull JavaParser javaParser,
                              @NonNull JavaDexClassLoader classLoader) {
        mJavaParser = javaParser;
        mClassLoader = classLoader;
    }

    @Override
    public boolean process(Editor editor, String unused,
                           ArrayList<SuggestItem> result) {
        try {
            JCCompilationUnit ast = mJavaParser.parse(editor.getText());
            prepare(editor, ast);

            Expression expr = getExpressionAtCursor(editor.getCursor());
            if (DLog.DEBUG) DLog.d(TAG, "expr = " + expr);
            if (expr == null) {
                return false;
            }

            return performComplete(expr.getExpression(), result);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean performComplete(JCExpression jcExpression, ArrayList<SuggestItem> result) {
        if (DLog.DEBUG) DLog.d(TAG, "analyse: class = " + jcExpression.getClass());
        if (jcExpression instanceof JCErroneous) {
            return analyseErroneous((JCErroneous) jcExpression, result);
        }
        return false;
    }

    private boolean analyseErroneous(JCErroneous jcErroneous, ArrayList<SuggestItem> result) {
        System.out.println("CompleteExpression.analyseErroneous");
        List<? extends JCTree> errorTrees = jcErroneous.getErrorTrees();
        JCTree tree = errorTrees.get(0);

        //s.toLower, incomplete method but give JCFieldAccess
        if (tree instanceof JCFieldAccess) {
            JCFieldAccess jcFieldAccess = (JCFieldAccess) tree;
            int startPosition = jcFieldAccess.getStartPosition();
            int cursorOffset = mCursor - startPosition;
            int incompleteLength = jcFieldAccess.getIdentifier().length();
            String incomplete = jcFieldAccess.getIdentifier().toString();
            //simple hack, improve later
            if (incomplete.equals("<error>")) {
                incomplete = "";
            }
            if (DLog.DEBUG) DLog.d(TAG, "incomplete = " + incomplete);

            JCExpression expression = jcFieldAccess.getExpression();
            IClass type = new TypeResolver(mClassLoader, mAst).resolveType(expression, mCursor);
            if (type != null) {
                ArrayList<MethodDescription> methods = type.getMethods();
                for (MethodDescription method : methods) {
                    if (method.getMethodName().startsWith(incomplete)) {
                        setInfo(method, mEditor, incomplete);
                        result.add(method);
                    }
                }
                ArrayList<FieldDescription> fields = type.getFields();
                for (FieldDescription field : fields) {
                    if (field.getFieldName().startsWith(incomplete)) {
                        setInfo(methods, mEditor, incomplete);
                        result.add(field);
                    }
                }

                return true;
            }
        }
        //s.toLower|Ca(), complete expression but wrong name
        else if (tree instanceof JCTree.JCMethodInvocation) {
        }
        return false;
    }

    private void resolveType(JCCompilationUnit mAst, JCExpression expression) {

    }

    @Override
    public void getSuggestion(Editor editor, String incomplete, List<SuggestItem> suggestItems) {

    }

    public void prepare(Editor editor, JCCompilationUnit ast) {
        mEditor = editor;
        mCursor = editor.getCursor();
        mAst = ast;
        mEndPositions = ast.endPositions;
    }

    @Nullable
    private Expression getExpressionAtCursor(int cursor) {
        //root
        for (JCTree jcTree : mAst.getTypeDecls()) {
            Expression expression = getExpressionContainsCursor(jcTree);
            if (expression != null) {
                return expression;
            }
        }
        return null;
    }

    @Nullable
    private Expression getExpressionContainsCursor(JCTree tree) {
        if (!isCursorInsideTree(tree)) {
            return null;
        }

        //class declared
        if (tree instanceof JCClassDecl) {
            return getExpressionFromClass((JCClassDecl) tree);
        }
        return null;
    }

    private Expression getExpressionFromStatement(JCStatement statement) {
        if (!isCursorInsideTree(statement)) {
            return null;
        }
        Expression result;
        if (statement instanceof JCBlock) {
            JCBlock jcBlock = (JCBlock) statement;
            List<JCStatement> statements = jcBlock.getStatements();
            return addRootIfNeeded(jcBlock/*root*/,
                    getExpressionFromStatements(statements));

        } else if (statement instanceof JCClassDecl) {
            return getExpressionFromClass((JCClassDecl) statement);

        } else if (statement instanceof JCDoWhileLoop) {
            JCDoWhileLoop jcDoWhileLoop = (JCDoWhileLoop) statement;
            JCExpression cond = jcDoWhileLoop.cond;
            if (isCursorInsideTree(cond)) {
                return new Expression(statement, cond);
            }

            JCStatement body = jcDoWhileLoop.getStatement();
            result = getExpressionFromStatement(body);
            return addRootIfNeeded(statement, result);

        } else if (statement instanceof JCEnhancedForLoop) {
            JCEnhancedForLoop jcEnhancedForLoop = (JCEnhancedForLoop) statement;
            JCExpression expression = jcEnhancedForLoop.getExpression();
            if (isCursorInsideTree(expression)) {
                return new Expression(jcEnhancedForLoop, expression);
            }

            JCVariableDecl variable = jcEnhancedForLoop.getVariable();
            return addRootIfNeeded(
                    jcEnhancedForLoop/*root*/,
                    getExpressionFromStatement(variable));

        } else if (statement instanceof JCExpressionStatement) {
            JCExpression expression = ((JCExpressionStatement) statement).getExpression();
            if (isCursorInsideTree(expression)) {
                return new Expression(statement, expression);
            }

        } else if (statement instanceof JCForLoop) {
            JCForLoop jcForLoop = (JCForLoop) statement;
            List<JCStatement> initializer = jcForLoop.getInitializer();
            result = getExpressionFromStatements(initializer);
            if (result != null) {
                return addRootIfNeeded(jcForLoop, result);
            }

            JCExpression condition = jcForLoop.getCondition();
            if (isCursorInsideTree(condition)) {
                return new Expression(jcForLoop, condition);
            }

            List<JCExpressionStatement> update = jcForLoop.getUpdate();
            result = getExpressionFromStatements(update);
            if (result != null) {
                return addRootIfNeeded(jcForLoop, result);
            }

            result = getExpressionFromStatement(jcForLoop.getStatement());
            if (result != null) {
                return addRootIfNeeded(jcForLoop, result);
            }
        } else if (statement instanceof JCIf) {
            JCIf jcIf = (JCIf) statement;
            JCExpression condition = jcIf.getCondition();
            if (isCursorInsideTree(condition)) {
                return new Expression(jcIf, condition);
            }

            JCStatement thenStatement = jcIf.getThenStatement();
            result = getExpressionFromStatement(thenStatement);
            if (result != null) {
                return addRootIfNeeded(jcIf, result);
            }

            JCStatement elseStatement = jcIf.getElseStatement();
            if (elseStatement != null) {
                result = getExpressionFromStatement(thenStatement);
                if (result != null) {
                    return addRootIfNeeded(jcIf, result);
                }

            }
        } else if (statement instanceof JCLabeledStatement) {
            JCLabeledStatement jcLabeledStatement = (JCLabeledStatement) statement;
            return addRootIfNeeded(jcLabeledStatement,
                    getExpressionFromStatement(jcLabeledStatement.getStatement()));

        } else if (statement instanceof JCReturn) {
            JCReturn jcReturn = (JCReturn) statement;
            JCExpression expression = jcReturn.getExpression();
            if (isCursorInsideTree(expression)) {
                return new Expression(jcReturn, expression);
            }
        } else if (statement instanceof JCSwitch) {
            JCSwitch jcSwitch = (JCSwitch) statement;
            JCExpression jcExpression = jcSwitch.getExpression();
            if (isCursorInsideTree(jcExpression)) {
                return new Expression(jcSwitch, jcExpression);
            }

            List<JCCase> jcCases = jcSwitch.getCases();
            for (JCCase jcCase : jcCases) {
                JCExpression jcCaseExpression = jcCase.getExpression();
                if (isCursorInsideTree(jcCaseExpression)) {
                    return new Expression(jcCase, jcCaseExpression);
                }
                List<JCStatement> statements = jcCase.getStatements();
                result = getExpressionFromStatements(statements);
                if (result != null) {
                    return result;
                }
            }
        } else if (statement instanceof JCSynchronized) {
            JCSynchronized jcSynchronized = (JCSynchronized) statement;
            JCExpression jcExpression = jcSynchronized.getExpression();
            if (isCursorInsideTree(jcExpression)) {
                return new Expression(jcSynchronized, jcExpression);
            }

            JCBlock block = jcSynchronized.getBlock();
            return addRootIfNeeded(
                    jcSynchronized/*root*/,
                    getExpressionFromStatement(block));

        } else if (statement instanceof JCThrow) {
            JCThrow jcThrow = (JCThrow) statement;
            if (isCursorInsideTree(jcThrow.getExpression())) {
                return new Expression(jcThrow, jcThrow.getExpression());
            }

        } else if (statement instanceof JCTry) {
            JCTry jcTry = (JCTry) statement;
            JCBlock block = jcTry.getBlock();
            result = getExpressionFromStatement(block);
            if (result != null) {
                return addRootIfNeeded(jcTry, result);
            }

            List<JCCatch> catches = jcTry.getCatches();
            for (JCCatch aCatch : catches) {
                JCVariableDecl parameter = aCatch.getParameter();
                result = getExpressionFromStatement(parameter);
                if (result == null) {
                    block = aCatch.getBlock();
                    result = getExpressionFromStatement(block);
                }
                if (result != null) {
                    result.addRoot(aCatch);
                    result.addRoot(jcTry);
                    return result;
                }
            }

            block = jcTry.getFinallyBlock();
            result = getExpressionFromStatement(block);
            return addRootIfNeeded(jcTry, result);

        } else if (statement instanceof JCVariableDecl) {
            JCVariableDecl jcVariableDecl = (JCVariableDecl) statement;
            JCExpression initializer = jcVariableDecl.getInitializer();
            if (isCursorInsideTree(initializer)) {
                return new Expression(jcVariableDecl, initializer);
            }
            JCExpression vartype = jcVariableDecl.vartype;
            if (isCursorInsideTree(vartype)) {
                return new Expression(jcVariableDecl, vartype);
            }
        } else if (statement instanceof JCWhileLoop) {
            JCWhileLoop jcWhileLoop = (JCWhileLoop) statement;
            JCExpression condition = jcWhileLoop.getCondition();
            if (isCursorInsideTree(condition)) {
                return new Expression(jcWhileLoop, condition);
            }

            JCStatement body = jcWhileLoop.getStatement();
            return addRootIfNeeded(
                    jcWhileLoop,
                    getExpressionFromStatement(body));
        }

        return null;
    }

    @Nullable
    private Expression getExpressionFromClass(JCClassDecl tree) {
        System.out.println("CompleteExpression.getStatementFromClass");

        if (!isCursorInsideTree(tree)) {
            return null;
        }
        Expression expression = null;
        //members of class: methods, field, inner class
        List<JCTree> members = tree.getMembers();
        for (JCTree member : members) {
            if (member instanceof JCMethodDecl) {
                expression = getExpressionFromMethod((JCMethodDecl) member);

            } else if (member instanceof JCVariableDecl) {
                expression = getExpressionFromStatement((JCVariableDecl) member);

            } else if (member instanceof JCClassDecl) {

                expression = getExpressionFromClass((JCClassDecl) member);
            }

            if (expression != null) {
                return addRootIfNeeded(tree, expression);
            }
        }
        return null;
    }

    @Nullable
    private Expression getExpressionFromMethod(JCMethodDecl method) {
        System.out.println("CompleteExpression.getExpressionFromMethod");

        Expression expression;
        List<JCVariableDecl> parameters = method.getParameters();

        for (JCVariableDecl parameter : parameters) {
            expression = getExpressionFromStatement(parameter);
            if (expression != null) {
                expression.addRoot(method);
                return expression;
            }
        }

        expression = getExpressionFromStatement(method.getBody());

        return addRootIfNeeded(method, expression);
    }

    @Nullable
    private Expression getExpressionFromStatements(
            @NonNull List<? extends JCStatement> statements) {
        for (JCStatement statement : statements) {
            Expression expression = getExpressionFromStatement(statement);
            if (expression != null) {
                return expression;
            }
        }
        return null;
    }

    private Expression addRootIfNeeded(JCTree root, Expression expression) {
        if (expression != null) {
            expression.addRoot(root);
            return expression;
        }
        return null;
    }

    private boolean isCursorInsideTree(JCTree tree) {
        if (tree == null) {
            return false;
        }
        int startPosition = tree.getStartPosition();
        int endPosition = tree.getEndPosition(mEndPositions);
        return startPosition <= mCursor && mCursor <= endPosition;
    }

}
