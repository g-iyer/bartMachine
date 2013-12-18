package bartMachine;

import java.util.ArrayList;
import Jama.Matrix;

public class bartMachine_j_linear_heteroskedasticity extends bartMachine_i_prior_cov_spec {

	private static final double DEFAULT_HYPER_SIGMA_WEIGHT = 1000;

	protected boolean use_linear_heteroskedastic_model;
	
	protected Matrix hyper_gamma_0;
	
	/** the variance of the errors as well as other things necessary for Gibbs sampling */
	protected double[][] gibbs_samples_of_sigsq_i;
	protected double[][] gibbs_samples_of_sigsq_i_after_burn_in;	
	protected Matrix[] gibbs_samples_of_gamma_for_lm_sigsqs;
	protected Matrix[] gibbs_samples_of_gamma_for_lm_sigsqs_after_burn_in;
	
	protected int m_h_num_accept_over_gibbs_samples;
	protected int m_h_num_accept_over_gibbs_samples_after_burn_in;

	private int p_Z;
	
	/** convenience caches */
	private ArrayList<Matrix> z_is_mc_t;
	private Matrix Sigmainv_times_gamma_0;
	private Matrix Bmat;
	private Matrix Bmat_inverse;
	private Matrix half_times_Z_mc_t_times_Z_mc;
	private Matrix half_times_Z_mc_t;

	private double[] hyper_sigma_weights;

	

	public void Build(){
		super.Build();
		if (use_linear_heteroskedastic_model){
//			for (int j = 0; j < p_Z; j++){
				double prop_accepted_tot = m_h_num_accept_over_gibbs_samples  / (double) num_gibbs_total_iterations;
				System.out.println("prop gibbs accepted tot: " + prop_accepted_tot);
//			}
			System.out.println("\n\n");
//			for (int j = 0; j < p_Z; j++){
				double prop_accepted_after_burn_in = m_h_num_accept_over_gibbs_samples_after_burn_in  / (double) (num_gibbs_total_iterations - num_gibbs_burn_in);
				System.out.println("prop gibbs accepted after burn in: " + prop_accepted_after_burn_in);
//			}
			System.out.println("\n\n");
			
			
			
			double gamma_j_avg = 0;
			double gamma_j_sd = 0;
			for (int j = 0; j < p_Z; j++){
				double[] gibbs_samples_gamma_j = new double[num_gibbs_total_iterations - num_gibbs_burn_in];
				for (int g = num_gibbs_burn_in; g < num_gibbs_total_iterations; g++){
					gibbs_samples_gamma_j[g - num_gibbs_burn_in] = gibbs_samples_of_gamma_for_lm_sigsqs[g].get(j, 0);
					gamma_j_avg = StatToolbox.sample_average(gibbs_samples_gamma_j);
					gamma_j_sd = StatToolbox.sample_standard_deviation(gibbs_samples_gamma_j);
				}
				System.out.println("gamma_" + (j + 1) + " = " + TreeIllustration.two_digit_format.format(gamma_j_avg) + " +- " + TreeIllustration.two_digit_format.format(gamma_j_sd) +
						((gamma_j_avg - 2 * gamma_j_sd < 0 && gamma_j_avg + 2 * gamma_j_sd > 0) ? " => plausibly 0" : " => *NOT* plausibly 0"));
			}
		}
	}
	
	public double[][] getGammas(){
		double[][] gammas = new double[num_gibbs_total_iterations][p + 1];
		for (int g = 0; g < num_gibbs_total_iterations; g++){
			for (int j = 0; j < p + 1; j++){
				gammas[g][j] = gibbs_samples_of_gamma_for_lm_sigsqs[g].get(j, 0); 
			}
			
		}
		return gammas;
	}

	public void setZ(){
		//first create the Z matrix and mean center it
		Matrix Z = new Matrix(n, p);
		for (int i = 0; i < n; i++){
			for (int j = 0; j < p; j++){
				Z.set(i, j, X_y.get(i)[j]);
			}
		}
		
		p_Z = Z.getColumnDimension();
//		System.out.println("p_Z: " + p_Z);
		
		Matrix ones = new Matrix(n, 1);
		for (int i = 0; i < n; i ++){
			ones.set(i, 0, 1);
		}
		Matrix quad_ones = ones.times((ones.transpose().times(ones)).inverse()).times(ones.transpose());		
		Matrix Z_mc = Z.minus(quad_ones.times(Z));
		
//		System.out.println("Z_mc: " + Z_mc.getRowDimension() + " x " + Z_mc.getColumnDimension());
//		Z_mc.print(3, 5);
		Matrix Z_mc_t = Z_mc.transpose();
		Matrix Z_mc_t_times_Z_mc = Z_mc_t.times(Z_mc);
		
		z_is_mc_t = new ArrayList<Matrix>(n);
		for (int i = 0; i < n; i ++){
			z_is_mc_t.add(Z_mc.getMatrix(i, i, 0, p_Z - 1));
//			System.out.println("z_is_mc_t[" + i + "]");
//			z_is_mc_t.get(i).print(2, 2);
		}	
		
		Matrix half = new Matrix(p_Z, p_Z);
		for (int j = 0; j < p_Z; j++){
			half.set(j, j, 0.5);
		}
		
		half_times_Z_mc_t = half.times(Z_mc_t);
		half_times_Z_mc_t_times_Z_mc = half.times(Z_mc_t_times_Z_mc);
		
		//if it hasn't been specified by the user, use default
		if (hyper_sigma_weights == null){			
			hyper_sigma_weights = new double[p_Z];
			for (int j = 0; j < p_Z; j++){
				hyper_sigma_weights[j] = DEFAULT_HYPER_SIGMA_WEIGHT; 
			}
		}
	}
	

	
	public void setData(ArrayList<double[]> X_y){
		super.setData(X_y);
		if (use_linear_heteroskedastic_model){
			System.out.println("use_linear_heteroskedasticity_model   n: " + n + " p: " + p);
			
			setZ(); //to be changed later when the user inputs Z on their own			
//			System.out.println("Z set");
			
			//set hyperparameters
			hyper_gamma_0 = new Matrix(p_Z, 1);			
			Matrix hyper_Sigma = new Matrix(p_Z, p_Z);
			for (int j = 0; j < p_Z; j++){
				hyper_Sigma.set(j, j, hyper_sigma_weights[j]);
			}
			
			///////////informed model
//			hyper_gamma_mean_vec.set(0, 0, 1);
//			hyper_gamma_mean_vec.set(1, 0, 0.3);
//			hyper_gamma_mean_vec.set(2, 0, 0.5);
//			hyper_gamma_mean_vec.set(3, 0, 0.3);
//			hyper_gamma_mean_vec.set(4, 0, 0.2);
//			hyper_gamma_mean_vec.set(5, 0, 0.5);
			System.out.println("hyper_gamma_0");
			hyper_gamma_0.print(2, 2);

			
			//now we can cache intermediate values we'll use everywhere
			Matrix Sigmainv = hyper_Sigma.inverse();
			Sigmainv_times_gamma_0 = Sigmainv.times(hyper_gamma_0);

			Bmat_inverse = (Sigmainv.plus(half_times_Z_mc_t_times_Z_mc));
			Bmat = Bmat_inverse.inverse();
		}
	}
	
	private double calcLnLikRatioGrowHeteroskedastic(bartMachineTreeNode grow_node) {
		double[] sigsqs = gibbs_samples_of_sigsq_i[gibbs_sample_num - 1];

		//we need sum_inv_sigsqs for the parent and both children
		//as well as weighted sum responses for the parent and both children
		double sum_inv_sigsq_parent = 0;
		double sum_responses_weighted_by_inv_sigsq_parent = 0;
		for (int i = 0; i < grow_node.n_eta; i++){
			int index = grow_node.indicies[i];
			double sigsq_i = sigsqs[index];
			sum_inv_sigsq_parent += 1 / sigsq_i;
			sum_responses_weighted_by_inv_sigsq_parent += grow_node.responses[i] / sigsq_i;
		}
		double sum_inv_sigsq_left = 0;
		double sum_responses_weighted_by_inv_sigsq_left = 0;
		for (int i = 0; i < grow_node.left.n_eta; i++){
			int index = grow_node.left.indicies[i];
			double sigsq_i = sigsqs[index];
			sum_inv_sigsq_left += 1 / sigsq_i;
			sum_responses_weighted_by_inv_sigsq_left += grow_node.left.responses[i] / sigsq_i;
		}
		double sum_inv_sigsq_right = 0;
		double sum_responses_weighted_by_inv_sigsq_right = 0;
		for (int i = 0; i < grow_node.right.n_eta; i++){
			int index = grow_node.right.indicies[i];
			double sigsq_i = sigsqs[index];
			sum_inv_sigsq_right += 1 / sigsq_i;
			sum_responses_weighted_by_inv_sigsq_right += grow_node.right.responses[i] / sigsq_i;
		}		
		
		double one_plus_hyper_sigsq_mu_times_sum_inv_sigsq_parent = 1 + hyper_sigsq_mu * sum_inv_sigsq_parent;
		double one_plus_hyper_sigsq_mu_times_sum_inv_sigsq_left = 1 + hyper_sigsq_mu * sum_inv_sigsq_left;
		double one_plus_hyper_sigsq_mu_times_sum_inv_sigsq_right = 1 + hyper_sigsq_mu * sum_inv_sigsq_right;
		
		double a = Math.log(one_plus_hyper_sigsq_mu_times_sum_inv_sigsq_parent);
		double b = Math.log(one_plus_hyper_sigsq_mu_times_sum_inv_sigsq_left);
		double c = Math.log(one_plus_hyper_sigsq_mu_times_sum_inv_sigsq_right);

		double d = Math.pow(sum_responses_weighted_by_inv_sigsq_left, 2) / one_plus_hyper_sigsq_mu_times_sum_inv_sigsq_left;
		double e = Math.pow(sum_responses_weighted_by_inv_sigsq_right, 2) / one_plus_hyper_sigsq_mu_times_sum_inv_sigsq_right;
		double f = Math.pow(sum_responses_weighted_by_inv_sigsq_parent, 2) / one_plus_hyper_sigsq_mu_times_sum_inv_sigsq_parent;
				
		return 0.5 * (a - b - c) + hyper_sigsq_mu / 2 * (d + e - f);
	}
	
	private double calcLnLikRatioChangeHeteroskedastic(bartMachineTreeNode eta, bartMachineTreeNode eta_star) {
		double[] sigsqs = gibbs_samples_of_sigsq_i[gibbs_sample_num - 1];
				
		double sum_inv_sigsq_ell_star = 0;
		double sum_responses_weighted_by_inv_sigsq_ell_star = 0;
		for (int i = 0; i < eta_star.left.n_eta; i++){
			int index = eta_star.left.indicies[i];
			double response_i = eta_star.left.responses[i];
			double sigsq_i = sigsqs[index];
			sum_inv_sigsq_ell_star += 1 / sigsq_i;
			sum_responses_weighted_by_inv_sigsq_ell_star += response_i / sigsq_i;
		}
		
		double sum_inv_sigsq_r_star = 0;
		double sum_responses_weighted_by_inv_sigsq_r_star = 0;
		for (int i = 0; i < eta_star.right.n_eta; i++){
			int index = eta_star.right.indicies[i];
			double response_i = eta_star.right.responses[i];
			double sigsq_i = sigsqs[index];
			sum_inv_sigsq_r_star += 1 / sigsq_i;
			sum_responses_weighted_by_inv_sigsq_r_star += response_i / sigsq_i;
		}
		
		double sum_inv_sigsq_ell = 0;
		double sum_responses_weighted_by_inv_sigsq_ell = 0;
		for (int i = 0; i < eta.left.n_eta; i++){
			int index = eta.left.indicies[i];
			double response_i = eta.left.responses[i];
			double sigsq_i = sigsqs[index];
			sum_inv_sigsq_ell += 1 / sigsq_i;
			sum_responses_weighted_by_inv_sigsq_ell += response_i / sigsq_i;
		}
		
		double sum_inv_sigsq_r = 0;
		double sum_responses_weighted_by_inv_sigsq_r = 0;
		for (int i = 0; i < eta.right.n_eta; i++){
			int index = eta.right.indicies[i];
			double response_i = eta.right.responses[i];
			double sigsq_i = sigsqs[index];
			sum_inv_sigsq_r += 1 / sigsq_i;
			sum_responses_weighted_by_inv_sigsq_r += response_i / sigsq_i;
		}	
		
		double one_plus_sigsq_mu_times_sum_one_over_sigsq_i_n_ell_star = 1 + hyper_sigsq_mu * sum_inv_sigsq_ell_star;
		double one_plus_sigsq_mu_times_sum_one_over_sigsq_i_n_r_star = 1 + hyper_sigsq_mu * sum_inv_sigsq_r_star;
		double one_plus_sigsq_mu_times_sum_one_over_sigsq_i_n_ell = 1 + hyper_sigsq_mu * sum_inv_sigsq_ell;
		double one_plus_sigsq_mu_times_sum_one_over_sigsq_i_n_r = 1 + hyper_sigsq_mu * sum_inv_sigsq_r;
		
		double a = Math.log(one_plus_sigsq_mu_times_sum_one_over_sigsq_i_n_ell);
		double b = Math.log(one_plus_sigsq_mu_times_sum_one_over_sigsq_i_n_r);
		double c = Math.log(one_plus_sigsq_mu_times_sum_one_over_sigsq_i_n_ell_star);
		double d = Math.log(one_plus_sigsq_mu_times_sum_one_over_sigsq_i_n_r_star);
		
		double e = Math.pow(sum_responses_weighted_by_inv_sigsq_ell_star, 2) / one_plus_sigsq_mu_times_sum_one_over_sigsq_i_n_ell_star;
		double f = Math.pow(sum_responses_weighted_by_inv_sigsq_r_star, 2) / one_plus_sigsq_mu_times_sum_one_over_sigsq_i_n_r_star;
		double g = Math.pow(sum_responses_weighted_by_inv_sigsq_ell, 2) / one_plus_sigsq_mu_times_sum_one_over_sigsq_i_n_ell;
		double h = Math.pow(sum_responses_weighted_by_inv_sigsq_r, 2) / one_plus_sigsq_mu_times_sum_one_over_sigsq_i_n_r;		
		
		return 0.5 * (a + b - c - d) + hyper_sigsq_mu / 2 * (e + f - g - h);
	}
	
	private void SampleSigsqsHeterogeneously(int sample_num, double[] es) {
		System.out.println("\n\nGibbs sample_num: " + sample_num + "  Sigsqs \n" + "----------------------------------------------------");
//		System.out.println("es: " + Tools.StringJoin(es));
//
//		System.out.println("s^2_e = " + StatToolbox.sample_variance(es));
		double[] es_untransformed = new double[n];
		double[] es_untransformed_sq = new double[n];
		for (int i = 0; i < n; i++){
			es_untransformed[i] = un_transform_e(es[i]);
			es_untransformed_sq[i] = Math.pow(es_untransformed[i], 2);
			
		}
//		System.out.println("es: " + Tools.StringJoin(es_untransformed));
//		System.out.println("es_sq: " + Tools.StringJoin(es_untransformed_sq));
		System.out.println("mse: " + Tools.sum_array(es_untransformed_sq) / n);
		//now we need to compute d_i for all data points
		Matrix gamma = gibbs_samples_of_gamma_for_lm_sigsqs[sample_num - 1];
		System.out.println("gamma: ");
		gamma.print(3, 5);
		
		double[] d_is = new double[n];
		for (int i = 0; i < n; i++){
			d_is[i] = Math.exp(z_is_mc_t.get(i).times(gamma).get(0, 0));
		}
//		System.out.println("old d_is: " + Tools.StringJoin(d_is));
		
		//now get the scale factor
		
		//hetero
		double sigsq = drawSigsqFromPosteriorForHeterogeneous(sample_num, es_untransformed, d_is);

		//homo
//		double sigsq = drawSigsqFromPosterior(sample_num, es);
		
		
		System.out.println("sigsq: " + sigsq);
		
		
		//now we need to draw a gamma
		Matrix gamma_draw = sampleGammaVecViaMH(gamma, es_untransformed_sq, sample_num, d_is, sigsq);
		System.out.println("gamma_draw: ");
		gamma_draw.print(3, 5);
		
//		gamma_draw.set(0, 0, 7);
		gibbs_samples_of_gamma_for_lm_sigsqs[sample_num] = gamma_draw;
		
		////inefficient: only do this if gamma changed
		for (int i = 0; i < n; i++){
			d_is[i] = Math.exp(z_is_mc_t.get(i).times(gamma_draw).get(0, 0));
		}
//		System.out.println("new d_is: " + Tools.StringJoin(d_is));
		
		//hetero
		for (int i = 0; i < n; i++){
			gibbs_samples_of_sigsq_i[sample_num][i] = transform_sigsq(sigsq) * d_is[i]; //make sure we re-transform them
		}
//		gibbs_samples_of_sigsq_i[sample_num] = transform_sigsq(TRUE_SIGSQS);
		
//		//homo
//		gibbs_samples_of_sigsq[sample_num] = sigsq;
		
//		System.out.println("gibbs_samples_of_sigsq_i: " + Tools.StringJoin(gibbs_samples_of_sigsq_i[sample_num]));
		
	}
	
	public static final double[] TRUE_SIGSQS = {14.7564438126604, 124.795176342157, 1.12822066179643, 49.7045018004305, 825.719038578746, 106.761862374075, 639.415909971605, 173.376491782475, 69.124333909772, 5.20559517036163, 23.7754703204292, 1.56971696307988, 70.7709256830803, 18.8950290758269, 19.1941117642176, 8.30221144867605, 498.14240638306, 4.77901349588003, 69.2135769429817, 93.8130379535436, 24.1994731688103, 9.76352722236251, 956.100439255812, 71.8310847362022, 388.272292540103, 4.0763898470658, 17.7635918135455, 71.2837098997644, 1.21674792994847, 143.102705950482, 1.98934871805753, 20.0819578318794, 3.31533519790039, 61.5773785986645, 82.5353185231496, 6.4410225245463, 7.70094631309298, 111.697245989711, 939.968045639186, 25.6974133981529, 139.227412500008, 1.65261733853617, 6.75178544976796, 199.905398649489, 507.91230437683, 257.028275861091, 131.989101120696, 86.1332070126934, 1096.15852161645, 13.3301102029082, 79.5417725873373, 28.4633723799568, 4.22508078684881, 27.7383063640925, 85.140361305758, 3.27573682059601, 16.9953802842945, 493.902072443857, 27.0327945952774, 8.39743506312853, 3.14288341615423, 262.626218692793, 463.096291236288, 21.9309876690995, 2.7759541103822, 210.908151758389, 18.2469949407178, 53.6493930779028, 6.85672436077896, 3.17231229731845, 228.815707701133, 3.01309097157997, 10.2395061662961, 32.9633579953923, 2.3447009858767, 6.76802751502552, 207.935651337173, 3.51202827773632, 5.56833861665702, 2.40753478428134, 241.716810254808, 38.4810389132474, 7.64925043173583, 112.329292067647, 161.693392094941, 12.3432146173595, 4.68947725140437, 1.07788849754766, 173.830272231542, 25.8395292793772, 2.27974292689905, 1.24295784380125, 2.01996807675772, 671.383571824707, 8.1954918449588, 2.91067068676492, 1.23853938738709, 3.11598781302569, 79.6794070764348, 563.769606396879};
	
	private double drawSigsqFromPosteriorForHeterogeneous(int sample_num, double[] es, double[] d_is) {
		//first calculate the SSE
		double weighted_sse = 0;
		for (int i = 0; i < n; i++){			
			weighted_sse += Math.pow(es[i], 2) * d_is[i]; 
		}
		//we're sampling from sigsq ~ InvGamma((nu + n) / 2, (sum_i wt_error^2_i + lambda * nu) / 2)
		//which is equivalent to sampling (1 / sigsq) ~ Gamma((nu + n) / 2, 2 / (sum_i error^2_i + lambda * nu))
		return StatToolbox.sample_from_inv_gamma((hyper_nu + es.length) / 2, 2 / (weighted_sse + hyper_nu * hyper_lambda)); //JB
	}

	private Matrix sampleGammaVecViaMH(Matrix gamma, double[] untransformed_es_sq, int sample_num, double[] d_is_current, double untransformed_sigsq) {
		System.out.println("===========sampleGammaVecViaMH begin");
		//generate the w vector from the old d_i's		
		Matrix w_gamma = new Matrix(n, 1);
		for (int i = 0; i < n; i++){
			double d_i = d_is_current[i];
			w_gamma.set(i, 0, Math.log(d_i) + untransformed_es_sq[i] / (untransformed_sigsq * d_i) - 1);
		}
		
//		System.out.println("Sigmainv_times_gamma_0: " + Sigmainv_times_gamma_0.getRowDimension() + " x " + Sigmainv_times_gamma_0.getColumnDimension());
//		Sigmainv_times_gamma_0.print(2, 2);
//		
//		System.out.println("half_times_Z_mc_t: " + half_times_Z_mc_t.getRowDimension() + " x " + half_times_Z_mc_t.getColumnDimension());
//		half_times_Z_mc_t.print(2, 2);
//		
//		System.out.println("w_gamma: " + w_gamma.getRowDimension() + " x " + w_gamma.getColumnDimension());
//		w_gamma.print(2, 2);
//		
//		System.out.println("Bmat: " + Bmat.getRowDimension() + " x " + Bmat.getColumnDimension());
//		Bmat.print(2, 2);
		
		Matrix a_gamma = Bmat.times(Sigmainv_times_gamma_0.plus(half_times_Z_mc_t.times(w_gamma)));
		
//		System.out.println("a_gamma: " + a_gamma.getRowDimension() + " x " + a_gamma.getColumnDimension());
//		a_gamma.print(2, 2);
		
		//now sample the new gamma proposal
		Matrix gamma_star = StatToolbox.sample_from_mult_norm_dist(a_gamma, Bmat);
		
		System.out.println("gamma_star: " + gamma_star.getRowDimension() + " x " + gamma_star.getColumnDimension());
		gamma_star.print(3, 5);
		
		//now we need a_gamma_star
		//generate the new d_i's
		double[] d_is_star = new double[n];
		for (int i = 0; i < n; i++){
			d_is_star[i] = Math.exp(z_is_mc_t.get(i).times(gamma_star).get(0, 0));
		}
		
		//generate the w vector from the new d_i's		
		Matrix w_gamma_star = new Matrix(n, 1);
		for (int i = 0; i < n; i++){
			double d_i = d_is_star[i];
			w_gamma_star.set(i, 0, Math.log(d_i) + untransformed_es_sq[i] / (untransformed_sigsq * d_i) - 1);
		}
		
		Matrix a_gamma_star = Bmat.times(Sigmainv_times_gamma_0.plus(half_times_Z_mc_t.times(w_gamma_star)));		
		
		//now we calculate the log MH ratio
		Matrix diff_term_left_transpose = gamma.minus(gamma_star).plus(a_gamma).minus(a_gamma_star).transpose();
		Matrix diff_term_right = gamma.plus(gamma_star).minus(a_gamma).minus(a_gamma_star);
		double top_term = diff_term_left_transpose.times(Bmat_inverse).times(diff_term_right).get(0, 0);
		
		double bottom_a = 0;
		double bottom_b = 0;
		Matrix gamma_minus_gamma_star = gamma.minus(gamma_star);
		
		for (int i = 0; i < n; i++){
			bottom_a += z_is_mc_t.get(i).times(gamma_minus_gamma_star).get(0, 0);
			bottom_b += untransformed_es_sq[i] * (Math.exp(-z_is_mc_t.get(i).times(gamma).get(0, 0)) - Math.exp(-z_is_mc_t.get(i).times(gamma_star).get(0, 0)));
		}
		
		double bottom_c = 0;
		for (int j = 0; j < p_Z; j++){
			double gamma_j = gamma.get(j, 0);
			double gamma_star_j = gamma_star.get(j, 0);
			double gamma_j_sq = Math.pow(gamma_j, 2);
			double gamma_j_star_sq = Math.pow(gamma_star_j, 2);
			double hyper_gamma_0_j = hyper_gamma_0.get(j, 0);
			bottom_c += 1 / hyper_sigma_weights[j] * (gamma_j_sq - gamma_j_star_sq + 2 * hyper_gamma_0_j * (gamma_star_j - gamma_j));
		}
		
		double log_mh_ratio = 0.5 * (top_term + bottom_a + 1 / untransformed_sigsq * bottom_b + bottom_c);
		
		
		System.out.println("log_mh_ratio: " + log_mh_ratio);
		
		double log_r = Math.log(StatToolbox.rand());
		if (log_r < log_mh_ratio){
			System.out.println("VAR ACCEPT MH");
			m_h_num_accept_over_gibbs_samples++;
			if (sample_num > num_gibbs_burn_in){
				m_h_num_accept_over_gibbs_samples_after_burn_in++;
			}
			System.out.println("===========sampleGammaVecViaMH end");
			return gamma_star;
		}
		System.out.println("VAR REJECT MH");
		System.out.println("===========sampleGammaVecViaMH end");
		return gamma;
	} 

	private void SampleMusWrapperWithHeterogeneity(int sample_num, int t) {
		
		bartMachineTreeNode previous_tree = gibbs_samples_of_bart_trees[sample_num - 1][t];
		//subtract out previous tree's yhats
		sum_resids_vec = Tools.subtract_arrays(sum_resids_vec, previous_tree.yhats);
		bartMachineTreeNode tree = gibbs_samples_of_bart_trees[sample_num][t];

		//homo
//		double current_sigsq = gibbs_samples_of_sigsq[sample_num - 1];
//		assignLeafValsBySamplingFromPosteriorMeanAndSigsqAndUpdateYhats(tree, current_sigsq);
		
		//hetero
		double[] current_sigsqs = gibbs_samples_of_sigsq_i[sample_num - 1];
		assignLeafValsBySamplingFromPosteriorMeanAndSigsqsAndUpdateYhatsWithHeterogeneity(tree, current_sigsqs);
		
		//after mus are sampled, we need to update the sum_resids_vec
		//add in current tree's yhats		
		sum_resids_vec = Tools.add_arrays(sum_resids_vec, tree.yhats);
	}	
	
	protected void assignLeafValsBySamplingFromPosteriorMeanAndSigsqsAndUpdateYhatsWithHeterogeneity(bartMachineTreeNode node, double[] sigsqs) {
//			System.out.println("assignLeafValsUsingPosteriorMeanAndCurrentSigsq sigsqs: " + Tools.StringJoin(sigsqs));
		if (node.isLeaf){
			
//				System.out.println("sigsq_from_vanilla_bart: " + sigsq_from_vanilla_bart + " 1 / sigsq_from_vanilla_bart: " + 1 / sigsq_from_vanilla_bart);
//				System.out.println("n = " + node.n_eta + " n over sigsq_from_vanilla_bart: " + node.n_eta / sigsq_from_vanilla_bart);
			
			//update ypred
			double posterior_var = calcLeafPosteriorVarWithHeterogeneity(node, sigsqs);
			//draw from posterior distribution
			double posterior_mean = calcLeafPosteriorMeanWithHeterogeneity(node, posterior_var, sigsqs);
//				System.out.println("assignLeafVals n_k = " + node.n_eta + " sum_nk_sq = " + Math.pow(node.n_eta, 2) + " node = " + node.stringLocation(true));
//				System.out.println("node responses: " + Tools.StringJoin(node.responses));
			node.y_pred = StatToolbox.sample_from_norm_dist(posterior_mean, posterior_var);
			
//				double posterior_mean_untransformed = un_transform_y(posterior_mean);
//				double posterior_sigma_untransformed = un_transform_y(Math.sqrt(posterior_var));
//				double y_pred_untransformed = un_transform_y(node.y_pred);
//				if (node.avg_response_untransformed() > 9){ 
//					double posterior_mean_vanilla_un = un_transform_y(node.sumResponses() / sigsq_from_vanilla_bart / (1 / hyper_sigsq_mu + node.n_eta / sigsq_from_vanilla_bart));
//					System.out.println("posterior_mean in BART = " + posterior_mean_vanilla_un);
				
//					System.out.println("posterior_mean in HBART = " + posterior_mean_untransformed + 
//							" node.avg_response = " + node.avg_response_untransformed() + 
//							" y_pred_untransformed = " + y_pred_untransformed + 
//							" posterior_sigma = " + posterior_sigma_untransformed + 
//							" hyper_sigsq_mu = " + hyper_sigsq_mu);
//				}
			
			
			if (node.y_pred == StatToolbox.ILLEGAL_FLAG){				
				node.y_pred = 0.0; //this could happen on an empty node
				System.err.println("ERROR assignLeafFINAL " + node.y_pred + " (sigsq = " + Tools.StringJoin(sigsqs) + ")");
			}
			//now update yhats
			node.updateYHatsWithPrediction();
//				System.out.println("assignLeafFINAL g = " + gibbs_sample_num + " y_hat = " + node.y_pred + " (sigsqs = " + Tools.StringJoin(sigsqs) + ")");
		}
		else {
			assignLeafValsBySamplingFromPosteriorMeanAndSigsqsAndUpdateYhatsWithHeterogeneity(node.left, sigsqs);
			assignLeafValsBySamplingFromPosteriorMeanAndSigsqsAndUpdateYhatsWithHeterogeneity(node.right, sigsqs);
		}
	}	
	
	private double calcLeafPosteriorMeanWithHeterogeneity(bartMachineTreeNode node, double posterior_var, double[] sigsqs) {		
		double numerator = 0;
		for (int ell = 0; ell < node.n_eta; ell++){
//				System.out.println("y_i = " + node.responses[ell] + " sigsq_i = " + sigsqs[node.indicies[ell]]);
			numerator += node.responses[ell] / sigsqs[node.indicies[ell]];
		}
//			System.out.println("calcLeafPosteriorMeanWithHeterogeneity numerator: " + numerator);
		return numerator * posterior_var;
	}

	private double calcLeafPosteriorVarWithHeterogeneity(bartMachineTreeNode node, double[] sigsqs) {
//			System.out.println("calcLeafPosteriorVarWithHeterogeneity sigsqs: " + Tools.StringJoin(sigsqs));
		double sum_one_over_sigsqs_leaf = 0;
//			System.out.print(" 1 / sigsqs: ");
		for (int index : node.indicies){
//				System.out.print( 1 / sigsqs[index] + ", ");
			sum_one_over_sigsqs_leaf += 1 / sigsqs[index];
		}
//			System.out.print("\n");
//			System.out.println("sum_one_over_sigsqs_leaf: " + sum_one_over_sigsqs_leaf);
		return 1 / (1 / hyper_sigsq_mu + sum_one_over_sigsqs_leaf);
	}

	/**
	 * We run the default initialization plus all initializations for our sigsq model
	 */
	protected void InitGibbsSamplingData(){
		super.InitGibbsSamplingData();
		if (use_linear_heteroskedastic_model){
			gibbs_samples_of_sigsq_i = new double[num_gibbs_total_iterations + 1][n];	
			gibbs_samples_of_sigsq_i_after_burn_in = new double[num_gibbs_total_iterations - num_gibbs_burn_in][n];
			gibbs_samples_of_gamma_for_lm_sigsqs = new Matrix[num_gibbs_total_iterations + 1];
			gibbs_samples_of_gamma_for_lm_sigsqs[0] = new Matrix(p_Z, 1); //start it up
			
			
			//set the beginning of the Gibbs chain to be the prior
			for (int j = 0; j < p_Z; j++){
				gibbs_samples_of_gamma_for_lm_sigsqs[0].set(j, 0, hyper_gamma_0.get(j, 0));
			}	
			
			gibbs_samples_of_gamma_for_lm_sigsqs_after_burn_in = new Matrix[num_gibbs_total_iterations - num_gibbs_burn_in];
		}	
	}	
	
	/**
	 * Instead of just setting one sigsq to the initial value, set sigsq's for all n observations to the initial value
	 */
	private void InitizializeSigsqHeteroskedastic() {
		double[] initial_sigsqs = gibbs_samples_of_sigsq_i[0];
		for (int i = 0; i < n; i++){
			initial_sigsqs[i] = StatToolbox.sample_from_inv_gamma(hyper_nu / 2, 2 / (hyper_nu * hyper_lambda));
		}	
	}	
	
	public ArrayList<double[]> getGibbsSamplesSigsqsHetero(){
		ArrayList<double[]> gibbs_samples_of_sigsq_i_arraylist = new ArrayList<double[]>(num_gibbs_total_iterations);
		for (int g = 0; g < num_gibbs_total_iterations; g++){
			gibbs_samples_of_sigsq_i_arraylist.add(gibbs_samples_of_sigsq_i[g]);
		}
		return gibbs_samples_of_sigsq_i_arraylist;				
	}
	
	/////////////nothing but scaffold code below, do not alter!

	protected void InitizializeSigsq() {
		super.InitizializeSigsq();
		if (use_linear_heteroskedastic_model){
			InitizializeSigsqHeteroskedastic();
		}
	}

	protected void SampleMusWrapper(int sample_num, int t) {
		if (use_linear_heteroskedastic_model){
			SampleMusWrapperWithHeterogeneity(sample_num, t);
		}
		else {
			super.SampleMusWrapper(sample_num, t);
		}
	}	

	protected void SampleSigsq(int sample_num, double[] es) {
		if (use_linear_heteroskedastic_model){
			SampleSigsqsHeterogeneously(sample_num, es);
		}
		else {
			super.SampleSigsq(sample_num, es);
		}		
	}

	protected double calcLnLikRatioGrow(bartMachineTreeNode grow_node) {
		if (use_linear_heteroskedastic_model){
			return calcLnLikRatioGrowHeteroskedastic(grow_node);
		}
		return super.calcLnLikRatioGrow(grow_node);
	}
	
	protected double calcLnLikRatioChange(bartMachineTreeNode eta, bartMachineTreeNode eta_star) {
		if (use_linear_heteroskedastic_model){
			return calcLnLikRatioChangeHeteroskedastic(eta, eta_star);
		}
		return super.calcLnLikRatioChange(eta, eta_star);
	}

	/**
	 * The user specifies this flag. Once set, the functions in this class are used over the default homoskedastic functions
	 * in parent classes
	 */
	public void useLinearHeteroskedasticModel(){
		use_linear_heteroskedastic_model = true;
	}
	
	public void setHyper_gamma_mean_vec(double[] hyper_gamma_mean_vec) {
		for (int j = 0; j < hyper_gamma_mean_vec.length; j++){
			this.hyper_gamma_0.set(j, 0, hyper_gamma_mean_vec[j]);
		}
	}
	
	public void setHyperSigmaWeights(double[] hyper_sigma_weights){
		this.hyper_sigma_weights = hyper_sigma_weights;
	}
}
