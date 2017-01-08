/*
 * Copyright (c) 2009-2017, Peter Abeles. All Rights Reserved.
 *
 * This file is part of Efficient Java Matrix Library (EJML).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ejml.alg.dense.linsol.qr;

import org.ejml.alg.dense.decomposition.TriangularSolver_R64;
import org.ejml.alg.dense.decomposition.qr.QRDecompositionHouseholderColumn_R64;
import org.ejml.alg.dense.decomposition.qr.QrHelperFunctions_R64;
import org.ejml.alg.dense.linsol.LinearSolverAbstract_R64;
import org.ejml.data.RowMatrix_F64;
import org.ejml.interfaces.decomposition.QRDecomposition;
import org.ejml.ops.SpecializedOps_R64;


/**
 * <p>
 * QR decomposition can be used to solve for systems.  However, this is not as computationally efficient
 * as LU decomposition and costs about 3n<sup>2</sup> flops.
 * </p>
 * <p>
 * It solve for x by first multiplying b by the transpose of Q then solving for the result.
 * <br>
 * QRx=b<br>
 * Rx=Q^T b<br>
 * </p>
 *
 * <p>
 * A column major decomposition is used in this solver.
 * <p>
 *
 * @author Peter Abeles
 */
public class LinearSolverQrHouseCol_R64 extends LinearSolverAbstract_R64 {

    private QRDecompositionHouseholderColumn_R64 decomposer;

    private RowMatrix_F64 a = new RowMatrix_F64(1,1);
    private RowMatrix_F64 temp = new RowMatrix_F64(1,1);

    protected int maxRows = -1;
    protected int maxCols = -1;

    private double[][] QR; // a column major QR matrix
    private RowMatrix_F64 R = new RowMatrix_F64(1,1);
    private double gammas[];

    /**
     * Creates a linear solver that uses QR decomposition.
     */
    public LinearSolverQrHouseCol_R64() {
        decomposer = new QRDecompositionHouseholderColumn_R64();
    }

    public void setMaxSize( int maxRows , int maxCols )
    {
        this.maxRows = maxRows; this.maxCols = maxCols;
    }

    /**
     * Performs QR decomposition on A
     *
     * @param A not modified.
     */
    @Override
    public boolean setA(RowMatrix_F64 A) {
        if( A.numRows < A.numCols )
            throw new IllegalArgumentException("Can't solve for wide systems.  More variables than equations.");
        if( A.numRows > maxRows || A.numCols > maxCols )
            setMaxSize(A.numRows,A.numCols);

        R.reshape(A.numCols,A.numCols);
        a.reshape(A.numRows,1);
        temp.reshape(A.numRows,1);

        _setA(A);
        if( !decomposer.decompose(A) )
            return false;

        gammas = decomposer.getGammas();
        QR = decomposer.getQR();
        decomposer.getR(R,true);
        return true;
    }

    @Override
    public /**/double quality() {
        return SpecializedOps_R64.qualityTriangular(R);
    }

    /**
     * Solves for X using the QR decomposition.
     *
     * @param B A matrix that is n by m.  Not modified.
     * @param X An n by m matrix where the solution is written to.  Modified.
     */
    @Override
    public void solve(RowMatrix_F64 B, RowMatrix_F64 X) {
        if( X.numRows != numCols )
            throw new IllegalArgumentException("Unexpected dimensions for X: X rows = "+X.numRows+" expected = "+numCols);
        else if( B.numRows != numRows || B.numCols != X.numCols )
            throw new IllegalArgumentException("Unexpected dimensions for B");

        int BnumCols = B.numCols;
        
        // solve each column one by one
        for( int colB = 0; colB < BnumCols; colB++ ) {

            // make a copy of this column in the vector
            for( int i = 0; i < numRows; i++ ) {
                a.data[i] = B.data[i*BnumCols + colB];
            }

            // Solve Qa=b
            // a = Q'b
            // a = Q_{n-1}...Q_2*Q_1*b
            //
            // Q_n*b = (I-gamma*u*u^T)*b = b - u*(gamma*U^T*b)
            for( int n = 0; n < numCols; n++ ) {
                double []u = QR[n];

                double vv = u[n];
                u[n] = 1;
                QrHelperFunctions_R64.rank1UpdateMultR(a, u, gammas[n], 0, n, numRows, temp.data);
                u[n] = vv;
            }

            // solve for Rx = b using the standard upper triangular solver
            TriangularSolver_R64.solveU(R.data,a.data,numCols);

            // save the results
            for( int i = 0; i < numCols; i++ ) {
                X.data[i*X.numCols+colB] = a.data[i];
            }
        }
    }

    @Override
    public boolean modifiesA() {
        return false;
    }

    @Override
    public boolean modifiesB() {
        return false;
    }

    @Override
    public QRDecomposition<RowMatrix_F64> getDecomposition() {
        return decomposer;
    }
}