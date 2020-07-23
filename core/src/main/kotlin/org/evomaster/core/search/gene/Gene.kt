package org.evomaster.core.search.gene

import org.evomaster.core.output.OutputFormat
import org.evomaster.core.search.service.AdaptiveParameterControl
import org.evomaster.core.search.service.Randomness
import org.evomaster.core.search.service.mutator.EvaluatedMutation
import org.evomaster.core.search.service.mutator.MutationWeightControl
import org.evomaster.core.search.service.mutator.genemutation.AdditionalGeneSelectionInfo
import org.evomaster.core.search.service.mutator.genemutation.ArchiveGeneMutator
import org.evomaster.core.search.service.mutator.genemutation.SubsetGeneSelectionStrategy


/**
 * A building block representing one part of an Individual.
 * The terms "gene" comes from the evolutionary algorithm literature
 */
abstract class Gene(var name: String) {

    init{
        if(name.isBlank()){
            throw IllegalArgumentException("Empty name for Gene")
        }
    }

    /**
     *  A gene could be inside a gene, in a tree-like structure.
     *  So for each gene, but the root, we keep track of its parent.
     *
     *  When a gene X is created with a child Y, then X is responsible
     *  to mark itself as parent of Y
     */
    var parent : Gene? = null

    /**
     * Follow the parent's path until the root of gene tree,
     * which could be this same gene
     */
    fun getRoot() : Gene{
        var curr = this
        while(curr.parent != null){
            curr = curr.parent!!
        }
        return curr
    }

    /**
     * Make a copy of this gene.
     *
     * Note: the [parent] of this gene will be [null], but all children
     * will have the correct parent
     */
    abstract fun copy() : Gene

    /**
     * weight for mutation
     * For example, higher the weight, the higher the chances to be selected for mutation
     */
    open fun mutationWeight() : Double = 1.0

    /**
     * Specify if this gene can be mutated during the search.
     * Typically, it will be true, apart from some special cases.
     */
    open fun isMutable() = true

    /**
     * Specify if this gene should be printed in the output test.
     * In other words, if this genotype directly influences the
     * phenotype
     */
    open fun isPrintable() = true


    /**
     *   Randomize the content of this gene.
     *
     *   @param randomness the source of non-determinism
     *   @param forceNewValue whether we should force the change of value. When we do mutation,
     *          it could otherwise happen that a value is replace with itself
     *   @param allGenes if the gene depends on the other (eg a Foreign Key in SQL databases),
     *          we need to refer to them
     */
    abstract fun randomize(
            randomness: Randomness,
            forceNewValue: Boolean,
            allGenes: List<Gene> = listOf())

    /**
     * A mutation is just a small change.
     * Apply a mutation to the current gene.
     * Regarding the gene,
     * 1) there might exist multiple internal genes i.e.,[candidatesInternalGenes].
     *  In this case, we first apply [selectSubset] to select a subset of internal genes.
     *  then apply mutation on each of the selected genes.
     * 2) When there is no need to do further selection, we apply [mutate] on the current gene.
     *
     *   @param randomness the source of non-determinism
     *   @param apc parameter control
     *   @param mwc mutation weight control
     *   @param allGenes if the gene depends on the other (eg a Foreign Key in SQL databases),
     *          we need to refer to them
     *   @param interalGeneSelectionStrategy a strategy to select internal genes to mutate
     *   @param enableAdaptiveMutation whether apply adaptive gene mutation, e.g., archive-based gene mutation
     *   @param additionalGeneMutationInfo contains additional info for gene mutation
     */
    fun standardMutation(
            randomness: Randomness,
            apc: AdaptiveParameterControl,
            mwc: MutationWeightControl,
            allGenes: List<Gene> = listOf(),
            internalGeneSelectionStrategy: SubsetGeneSelectionStrategy = SubsetGeneSelectionStrategy.DEFAULT,
            enableAdaptiveGeneMutation: Boolean = false,
            additionalGeneMutationInfo: AdditionalGeneSelectionInfo? = null
    ){
        //if impact is not able to obtain, adaptive-gene-mutation should also be disabled
        val internalGenes = candidatesInternalGenes(randomness, apc, allGenes, internalGeneSelectionStrategy, enableAdaptiveGeneMutation, additionalGeneMutationInfo)
        if (internalGenes.isEmpty()){
            val mutated = mutate(randomness, apc, mwc, allGenes, internalGeneSelectionStrategy, enableAdaptiveGeneMutation, additionalGeneMutationInfo)
            if (!mutated) throw IllegalStateException("leaf mutation is not implemented")
        }else{
            val selected = selectSubset(internalGenes, randomness, apc, mwc, allGenes, internalGeneSelectionStrategy, enableAdaptiveGeneMutation, additionalGeneMutationInfo)

            selected.forEach{
                do {
                    it.first.standardMutation(randomness, apc, mwc, allGenes, internalGeneSelectionStrategy, enableAdaptiveGeneMutation, it.second)
                }while (!mutationCheck())
            }
        }
    }

    /**
     * mutated gene should pass the check if needed, eg, DateGene
     *
     * In some cases, we must have genes with 'valid' values.
     * For example, a date with month 42 would be invalid.
     * On the one hand, it can still be useful for robustness testing
     * to provide such invalid values in a HTTP call. On the other hand,
     * it would be pointless to try to add it directly into a database,
     * as that SQL command would simply fail without any SUT code involved.
     */
    open fun mutationCheck() : Boolean = true

    /**
     * @return whether to apply a subset selection for internal genes to mutate
     */
    open fun candidatesInternalGenes(randomness: Randomness, apc: AdaptiveParameterControl, allGenes: List<Gene>, selectionStrategy: SubsetGeneSelectionStrategy, enableAdaptiveGeneMutation: Boolean, additionalGeneMutationInfo: AdditionalGeneSelectionInfo?)
            = listOf<Gene>()

    /**
     * @return a subset of internal genes to apply mutations
     */
    open fun selectSubset(internalGenes: List<Gene>,
                          randomness: Randomness,
                          apc: AdaptiveParameterControl,
                          mwc: MutationWeightControl,
                          allGenes: List<Gene> = listOf(),
                          selectionStrategy: SubsetGeneSelectionStrategy,
                          enableAdaptiveGeneMutation: Boolean,
                          additionalGeneMutationInfo: AdditionalGeneSelectionInfo?): List<Pair<Gene, AdditionalGeneSelectionInfo?>> {
        return  when(selectionStrategy){
            SubsetGeneSelectionStrategy.DEFAULT -> listOf(Pair(randomness.choose(internalGenes), additionalGeneMutationInfo))
            SubsetGeneSelectionStrategy.DETERMINISTIC_WEIGHT -> mwc.selectSubGene(candidateGenesToMutate = internalGenes, adaptiveWeight = false).map { it to additionalGeneMutationInfo }
            SubsetGeneSelectionStrategy.ADAPTIVE_WEIGHT -> {
                additionalGeneMutationInfo?: throw IllegalArgumentException("additionalGeneSelectionInfo should not be null")
                if (additionalGeneMutationInfo.impact == null)
                    mwc.selectSubGene(candidateGenesToMutate = internalGenes, adaptiveWeight = false).map { it to additionalGeneMutationInfo }
                else
                    adaptiveSelectSubset(randomness, internalGenes, mwc, additionalGeneMutationInfo)
            }
        }.also {
            if (it.isEmpty())
                throw IllegalStateException("with $selectionStrategy strategy and ${internalGenes.size} candidates, none is selected to mutate")
        }
    }

    open fun adaptiveSelectSubset(randomness: Randomness,
                                  internalGenes: List<Gene>,
                                  mwc: MutationWeightControl,
                                  additionalGeneMutationInfo: AdditionalGeneSelectionInfo): List<Pair<Gene, AdditionalGeneSelectionInfo?>> {
        throw IllegalStateException("adaptive gene selection is unavailable for the gene")
    }

    /**
     * mutate the current gene if there is no need to apply selection, i.e., when [candidatesInternalGenes] is empty
     */
    open fun mutate(randomness: Randomness,
                    apc: AdaptiveParameterControl,
                    mwc: MutationWeightControl,
                    allGenes: List<Gene> = listOf(),
                    selectionStrategy: SubsetGeneSelectionStrategy,
                    enableAdaptiveGeneMutation: Boolean,
                    additionalGeneMutationInfo: AdditionalGeneSelectionInfo?) = false

    /**
     * Return the value as a printable string.
     * Once printed, it would be equivalent to the actual value, eg
     *
     * 1 -> "1" -> printed as 1
     *
     * "foo" -> "\"foo\"" -> printed as "foo"
     *
     * @param previousGenes previous genes which are necessary to look at
     * to determine the actual value of this gene
     * @param mode some genes could be printed in different ways, like an
     * object printed as JSON or XML
     * @param targetFormat different target formats may have different rules
     * regarding what characters need to be escaped (e.g. the $ char in Kotlin)
     * If the [targetFormat] is set to null, no characters are escaped.
     */
    abstract fun getValueAsPrintableString(
            previousGenes: List<Gene> = listOf(),
            mode: GeneUtils.EscapeMode? = null,
            targetFormat: OutputFormat? = null
    ) : String


    open fun getValueAsRawString() = getValueAsPrintableString(targetFormat = null)
    /*
    Note: above, null target format means that no characters are escaped.
     */

    abstract fun copyValueFrom(other: Gene)

    /**
     * If this gene represents a variable, then return its name.
     */
    open fun getVariableName() = name

    /**
     * Genes might have other genes inside (eg, think of array).
     * @param excludePredicate is used to configure which genes you do not want to show genes inside.
     *      For instance, an excludePredicate is {gene : Gene -> (gene is TimeGene)}, then when flatView of a Gene including TimeGene,
     *      the genes inside e.g., hour: IntegerGene will be not viewed, but TimeGene will be viewed.
     * @return a recursive list of all nested genes, "this" included
     */
    open fun flatView(excludePredicate: (Gene) -> Boolean = {false}): List<Gene>{
        return listOf(this)
    }

    /**
     * Genes might contain a value that is also stored
     * in another gene of the same type.
     */
    abstract fun containsSameValueAs(other: Gene): Boolean

    /**
     * indicates if it is likely that the gene reaches its optimal value, i.e., all possible values have been evaluated during search in the context of its individual.
     * For instance, an enum has four items. If all values evaluated used during search, its 'Optimal' may be identified. But there may exist dependency among the genes
     * in an individual, 'Optimal' can be reset.
     */
    open fun reachOptimal(targets: Set<Int>) = false

    /**
     * based on evaluated results, update a preferred boundary for the gene
     * @param original the gene before mutation
     * @param mutated the mutated gene
     * @param targetsEvaluated updated targets after the gene is mutated. the key is target and the value indicates 0 -- no change, -1 -- become worse, 1 -- become better
     * @param archiveMutator archive mutator
     */
    open fun archiveMutationUpdate(original: Gene, mutated: Gene, targetsEvaluated: Map<Int, EvaluatedMutation>, archiveMutator: ArchiveGeneMutator){
        //do nothing
    }
}

