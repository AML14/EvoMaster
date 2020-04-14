package org.evomaster.core.search.impact

import org.evomaster.core.search.Action
import org.evomaster.core.search.FitnessValue
import org.evomaster.core.search.Individual
import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.service.mutator.MutatedGeneSpecification

/**
 * created by manzh on 2019-10-31
 */
class ImpactsOfIndividual private constructor(
        /**
         * list of impacts per action in initialization of an individual
         */
        //private val initializationGeneImpacts : MutableList<ImpactsOfAction>,
        private val initializationGeneImpacts: InitializationActionImpacts,

        /**
         * list of impacts per action in actions of an individual
         */
        private val actionGeneImpacts: MutableList<ImpactsOfAction>,

        /**
         * a history of structures of [this] with best fitness
         */
        val impactsOfStructure: ActionStructureImpact = ActionStructureImpact("StructureSize"),

        /**
         * key -> target id
         * value -> fitness value
         */
        val reachedTargets: MutableMap<Int, Double> = mutableMapOf(),

        private val maxSqlInitActionsPerMissingData: Int
) {

    constructor(individual: Individual, abstractInitializationGeneToMutate: Boolean, maxSqlInitActionsPerMissingData: Int, fitnessValue: FitnessValue?) : this(
            initializationGeneImpacts = InitializationActionImpacts(abstractInitializationGeneToMutate),//individual.seeInitializingActions().map { a -> ImpactsOfAction(a) }.toMutableList(),
            actionGeneImpacts = if (individual.seeActions().isEmpty()) mutableListOf(ImpactsOfAction(individual, individual.seeGenes())) else individual.seeActions().map { a -> ImpactsOfAction(a) }.toMutableList(),
            maxSqlInitActionsPerMissingData = maxSqlInitActionsPerMissingData
    ) {
        if (fitnessValue != null) {
            impactsOfStructure.updateStructure(individual, fitnessValue)
            fitnessValue.getViewOfData().forEach { (t, u) ->
                reachedTargets[t] = u.distance
            }
        }
    }

    constructor(abstractInitializationGeneToMutate: Boolean) : this(InitializationActionImpacts(abstractInitializationGeneToMutate), mutableListOf(), maxSqlInitActionsPerMissingData = 0)

    /**
     * @param groupedActions actions are grouped
     * @param sequenceSensitive when it is true, complete sequence structure, otherwise, only keep uniques ones
     */
    private fun initInitializationAction(groupedActions: List<List<Action>>, sequenceSensitive: Boolean = false): MutableList<ImpactsOfAction> {
        if (sequenceSensitive) return groupedActions.flatMap { it.map { a -> ImpactsOfAction(a) } }.toMutableList()

        /*
            there might exist duplicated db actions with EvoMaster that
                1) ensures that required resources are created; 2) support collection request, e.g., GET Collection
            However in a view of mutation, we only concern unique ones
            therefore we abstract dbactions in Initialization in order to identify unique dbactions,
            then further mutate values with abstracted ones, e.g.,
                a sequence of dbaction is abababc, then its abstraction is ab-c
         */
        TODO()

    }

    fun copy(): ImpactsOfIndividual {
        return ImpactsOfIndividual(
                //initializationGeneImpacts.map { it.copy() }.toMutableList(),
                initializationGeneImpacts.copy(),
                actionGeneImpacts.map { it.copy() }.toMutableList(),
                impactsOfStructure.copy(),
                reachedTargets.map { it.key to it.value }.toMap().toMutableMap(),
                maxSqlInitActionsPerMissingData
        )
    }

    fun clone(): ImpactsOfIndividual {
        return ImpactsOfIndividual(
                //initializationGeneImpacts.map { it.clone() }.toMutableList(),
                initializationGeneImpacts.clone(),
                actionGeneImpacts.map { it.clone() }.toMutableList(),
                impactsOfStructure.clone(),
                reachedTargets.map { it.key to it.value }.toMap().toMutableMap(),
                maxSqlInitActionsPerMissingData
        )
    }


    fun getSizeOfActionImpacts(fromInitialization: Boolean) = if (fromInitialization) initializationGeneImpacts.getSize() else actionGeneImpacts.size

    /**
     * @param actionIndex is null when there is no action in the individual, then return the first GeneImpact
     */
    fun getGene(actionName: String?, geneId: String, actionIndex: Int?, fromInitialization: Boolean): GeneImpact? {
        actionIndex ?: return actionGeneImpacts.first().geneImpacts[geneId]
        val impactsOfAction =
                if (fromInitialization) initializationGeneImpacts.getImpactOfAction(actionName, actionIndex)
                else getImpactsAction(actionName, actionIndex)
        impactsOfAction ?: return null
        return impactsOfAction.get(geneId, actionName)
    }

    /**
     * @return all genes whose name is [geneId]
     */
    fun getGeneImpact(geneId: String): List<GeneImpact> {
        val list = mutableListOf<GeneImpact>()

        initializationGeneImpacts.getAll().plus(actionGeneImpacts).forEach {
            if (it.geneImpacts.containsKey(geneId))
                list.add(it.geneImpacts[geneId]!!)
        }
        return list
    }

    fun syncBasedOnIndividual(individual: Individual, mutatedGene: MutatedGeneSpecification) {
        //for initialization due to db action fixing
        val diff = individual.seeInitializingActions().size - initializationGeneImpacts.getOriginalSize()
        if (diff != 0) { //truncation
            initializationGeneImpacts.truncation(mutatedGene.addedInitializationGroup, individual.seeInitializingActions())
        }

        //for action
        if (individual.seeActions().size != actionGeneImpacts.size)
            throw IllegalArgumentException("inconsistent size of actions and impacts")
        individual.seeActions().forEach { action ->
            val actionName = action.getName()
            val index = individual.seeActions().indexOf(action)
            action.seeGenes().filter { !mutatedGene.allManipulatedGenes().contains(it) }.forEach { g ->
                val id = ImpactUtils.generateGeneId(action, g)
                if (getGene(actionName, id, index, false) == null) {
                    val impact = ImpactUtils.createGeneImpact(g, id)
                    actionGeneImpacts[index].addGeneImpact(actionName, impact)
                }
            }
        }
    }

    fun deleteActionGeneImpacts(actionIndex: Set<Int>): Boolean {
        if (actionIndex.isEmpty()) return false
        if (actionIndex.max()!! >= actionGeneImpacts.size)
            return false
        actionIndex.sortedDescending().forEach {
            actionGeneImpacts.removeAt(it)
        }
        return true
    }


    fun initInitializationImpacts(groupedActions: List<List<Action>>) {
        initializationGeneImpacts.initInitializationActions(groupedActions)
    }

    fun updateInitializationGeneImpacts(other : ImpactsOfIndividual) {
        initializationGeneImpacts.initInitializationActions(other.initializationGeneImpacts)
    }

    fun addOrUpdateActionGeneImpacts(actionName: String?, actionIndex: Int, newAction: Boolean, impacts: MutableMap<String, GeneImpact>): Boolean {
        if (newAction) {
            if (actionIndex > actionGeneImpacts.size) return false
            actionGeneImpacts.add(actionIndex, ImpactsOfAction(actionName, impacts))
            return true
        }
        if (actionIndex >= actionGeneImpacts.size) return false
        return actionGeneImpacts[actionIndex].addGeneImpact(actionName, impacts)
    }

    fun anyImpactfulInfo(): Boolean {
        for (a in initializationGeneImpacts.getAll().plus(actionGeneImpacts)) {
            if (a.anyImpactfulInfo()) return true
        }
        return false
    }

    fun flattenAllGeneImpact(): List<GeneImpact> {
        return initializationGeneImpacts.getAll().plus(actionGeneImpacts).flatMap { it.geneImpacts.values }
    }

    fun getInitializationGeneImpact(): List<MutableMap<String, GeneImpact>> {
        return initializationGeneImpacts.getAll().map { it.geneImpacts }
    }

    fun getActionGeneImpact(): List<MutableMap<String, GeneImpact>> {
        return actionGeneImpacts.map { it.geneImpacts }
    }

    fun anyImpactInfo(): Boolean = initializationGeneImpacts.getSize() > 0 || actionGeneImpacts.isNotEmpty()

    private fun getImpactsAction(actionName: String?, actionIndex: Int): ImpactsOfAction {
        if (actionIndex >= actionGeneImpacts.size)
            throw IllegalArgumentException("exceed the boundary of impacts regarding actions, i.e., size of actions is ${actionGeneImpacts.size}, but asking index is $actionIndex")
        val actionImpacts = actionGeneImpacts[actionIndex]
        if (actionName != null && actionImpacts.actionName != actionName)
            throw IllegalArgumentException("mismatched action name, i.e., current is ${actionImpacts.actionName}, but $actionName")
        return actionImpacts
    }

    fun findImpactsByAction(actionName: String, actionIndex: Int, fromInitialization: Boolean): MutableMap<String, GeneImpact>? {
        val found = findImpactsAction(actionName, actionIndex, fromInitialization) ?: return null
        return found.geneImpacts
    }

    private fun findImpactsAction(actionName: String, actionIndex: Int, fromInitialization: Boolean): ImpactsOfAction? {
        return try {
            if (fromInitialization) initializationGeneImpacts.getImpactOfAction(actionName, actionIndex)
            else getImpactsAction(actionName, actionIndex)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * @property actionName name of action if action exists, versus null
     * @property geneImpacts impact info of genes of the action or the individual (actionName == null)
     */
    private data class ImpactsOfAction(val actionName: String?, val geneImpacts: MutableMap<String, GeneImpact> = mutableMapOf()) {
        fun copy(): ImpactsOfAction {
            return ImpactsOfAction(actionName, geneImpacts.map { it.key to it.value.copy() }.toMap().toMutableMap())
        }

        fun clone(): ImpactsOfAction {
            return ImpactsOfAction(actionName, geneImpacts.map { it.key to it.value.clone() }.toMap().toMutableMap())
        }

        constructor(action: Action) : this(
                actionName = action.getName(),
                geneImpacts = action.seeGenes().map {
                    val id = ImpactUtils.generateGeneId(action, it)
                    id to ImpactUtils.createGeneImpact(it, id)
                }.toMap().toMutableMap())

        constructor(individual: Individual, genes: List<Gene>) : this(
                actionName = null,
                geneImpacts = genes.map {
                    val id = ImpactUtils.generateGeneId(individual, it)
                    id to ImpactUtils.createGeneImpact(it, id)
                }.toMap().toMutableMap()
        )

        constructor(actionName: String?, geneImpacts: List<GeneImpact>) : this(
                actionName = actionName,
                geneImpacts = geneImpacts.map { it.getId() to it }.toMap().toMutableMap()
        )

        /**
         * @return false mismatched action name
         */
        fun addGeneImpact(actionName: String?, geneImpact: GeneImpact, forceUpdate: Boolean = false): Boolean {
            val name = actionName ?: ImpactUtils.extractActionName(geneImpact.getId())
            if (name != actionName) return false

            if (forceUpdate && geneImpacts.containsKey(geneImpact.getId()))
                geneImpacts.replace(geneImpact.getId(), geneImpact)
            else
                geneImpacts.putIfAbsent(geneImpact.getId(), geneImpact)

            return true
        }

        /**
         * @return false mismatched action name
         */
        fun addGeneImpact(actionName: String?, geneImpact: MutableMap<String, GeneImpact>, forceUpdate: Boolean = false): Boolean {
            val mismatched = actionName != this.actionName || geneImpact.any { ImpactUtils.extractActionName(it.key) != this.actionName }
            if (mismatched) return false

            geneImpact.forEach { (t, u) ->
                if (forceUpdate && geneImpacts.containsKey(t))
                    geneImpacts.replace(t, u)
                else
                    geneImpacts.putIfAbsent(t, u)
            }
            return true
        }

        fun exists(geneId: String, actionName: String?): Boolean? {
            val name = actionName ?: ImpactUtils.extractActionName(geneId)
            if (name != actionName) return null
            return geneImpacts.containsKey(geneId)
        }

        fun get(geneId: String, actionName: String?): GeneImpact? {
            val name = actionName ?: ImpactUtils.extractActionName(geneId)
            if (name != actionName) throw IllegalArgumentException("mismatched action name, i.e., current is ${this.actionName}, but $actionName")
            return geneImpacts[geneId]
        }

        fun anyImpactfulInfo(): Boolean = geneImpacts.any { it.value.getTimesOfImpacts().any { i -> i.value > 0 } }

        fun getImpactfulTargets(): Set<Int> = geneImpacts.values.flatMap { it.getTimesOfImpacts().filter { i -> i.value > 0 }.keys }.toSet()

        fun getNoImpactTargets(): Set<Int> = geneImpacts.values.flatMap { it.getTimesOfNoImpactWithTargets().filter { i -> i.value > 0 }.keys }.toSet()

        fun isMissing(actionName: String?, geneId: String): Boolean? {
            val name = actionName ?: ImpactUtils.extractActionName(geneId)
            if (name != actionName) return null
            return !geneImpacts.containsKey(geneId)
        }
    }

    /**
     * impacts of actions for initialization of a test.
     * Currently, the initialization is composed of a sequence of SQL actions, and
     * there exist some duplicated sub-sequence.
     * @property abstract indicates whether extract the actions in order to identify unique sub-sequence.
     * @property enableImpactOnDuplicatedTimes indicates whether collect impacts on duplicated times.
     */
    private class InitializationActionImpacts(val abstract: Boolean, val enableImpactOnDuplicatedTimes : Boolean = false) : ImpactOfDuplicatedArtifact<ImpactsOfAction>() {

        /**
         * index conforms with completeSequence
         * first of pair is name of template
         * second of pair is index of actions in this template
         */
        val indexMap = mutableListOf<Pair<String, Int>>()

        constructor(groupedActions: List<List<Action>>, abstract: Boolean) : this(abstract) {
            initInitializationActions(groupedActions)
        }

        private fun initPreCheck(){
            if (completeSequence.isNotEmpty() || template.isNotEmpty() || indexMap.isNotEmpty()) throw IllegalStateException("duplicated initialization")
        }

        fun initInitializationActions(groupedActions: List<List<Action>>) {
            initPreCheck()

            val groups = groupedActions.map { it.map { a -> ImpactsOfAction(a) } }
            completeSequence.addAll(groups.flatten())
            if (abstract) {
                groups.forEach { t ->
                    val key = generateTemplateKey(t.map { it.actionName!! })
                    template.putIfAbsent(key, t)
                    if (enableImpactOnDuplicatedTimes)
                        templateDuplicateTimes.putIfAbsent(key, Impact(id = key))

                    t.forEachIndexed { i, _ ->
                        indexMap.add(Pair(key, i))
                    }
                }
            }
        }

        fun initInitializationActions(impact: InitializationActionImpacts) {
            initPreCheck()
            clone(impact)
        }

        /**
         * @param groupedActions original initialized actions
         * @param list actions after truncation
         */
        fun truncation(groupedActions: List<List<Action>>, list: List<Action>) {
            val original = completeSequence.size

            if (list.size > original) throw IllegalArgumentException("there are more db actions after the truncation")
            if (list.size == original) return

            val newCompleteSequence = list.mapIndexed { index, db ->
                val name = db.getName()
                //FIXME Man: further check null case
                getImpactOfAction(name, index) ?: ImpactsOfAction(db)
            }

            if (!abstract) {
                completeSequence.clear()
                completeSequence.addAll(newCompleteSequence)
                return
            }

            //update template
            indexMap.clear()
            val removal = groupedActions.flatten().subList(list.size, original)
            val newKeys = groupedActions.mapNotNull { g ->
                val inner = g.filter { a -> !removal.contains(a) }
                if (inner.isEmpty()) null
                else{
                    val key = generateTemplateKey(inner.map { a -> a.getName() })
                    if(!template.containsKey(key)){
                        template[key] = completeSequence.subList(indexMap.size, indexMap.size + g.size -1)
                    }
                    indexMap.addAll(inner.mapIndexed { index, _ -> Pair(key, index) })
                    key
                }

            }.toSet()

            //remove template
            template.keys.filterNot { newKeys.contains(it) }.forEach {
                template.remove(it)
            }
        }

        private fun generateTemplateKey(actionNames: List<String>) = actionNames.joinToString("$$")

        fun getTemplateValue(group: List<Action>): List<ImpactsOfAction>? {
            return template[generateTemplateKey(group.map { it.getName() })]
        }

        fun copy(): InitializationActionImpacts {
            val new = InitializationActionImpacts(abstract, enableImpactOnDuplicatedTimes)
            new.completeSequence.addAll(completeSequence.map { it.copy() })
            new.template.putAll(template.mapValues { it.value.map { v -> v.copy() } })
            if(enableImpactOnDuplicatedTimes)
                new.templateDuplicateTimes.putAll(templateDuplicateTimes.mapValues { it.value.copy() })
            return new
        }


        fun clone(): InitializationActionImpacts {
            val new = InitializationActionImpacts(abstract, enableImpactOnDuplicatedTimes)
            new.completeSequence.addAll(completeSequence.map { it.clone() })
            new.template.putAll(template.mapValues { it.value.map { v -> v.clone() } })
            if(enableImpactOnDuplicatedTimes)
                new.templateDuplicateTimes.putAll(templateDuplicateTimes.mapValues { it.value.clone() })
            return new
        }

        /**
         * clone this based on [other]
         */
        fun clone(other: InitializationActionImpacts) {
            reset()
            completeSequence.addAll(other.completeSequence.map { it.clone() })
            template.putAll(other.template.mapValues { it.value.map { v -> v.clone() } })
            if(enableImpactOnDuplicatedTimes != other.enableImpactOnDuplicatedTimes)
                throw IllegalStateException("different setting on enableImpactOnDuplicatedTimes")
            if(enableImpactOnDuplicatedTimes)
               templateDuplicateTimes.putAll(other.templateDuplicateTimes.mapValues { it.value.clone() })
        }

        fun reset(){
            completeSequence.clear()
            template.clear()
            indexMap.clear()
            templateDuplicateTimes.clear()
        }
        /**
         * @return impact of action by [actionName] or [actionIndex]
         * @param actionName is a name of action
         * @param actionIndex index of action in a test
         */
        fun getImpactOfAction(actionName: String?, actionIndex: Int): ImpactsOfAction? {
            if (actionIndex >= completeSequence.size)
                throw IllegalArgumentException("exceed the boundary of impacts regarding actions, i.e., size of actions is ${completeSequence.size}, but asking index is $actionIndex")
            val name = completeSequence[actionIndex].actionName
            if (actionName != null && name != actionName)
                throw IllegalArgumentException("mismatched action name, i.e., current is $name, but $actionName")
            if (!abstract) {
                return completeSequence[actionIndex]
            }
            val templateInfo = indexMap[actionIndex]
            return template[templateInfo.first]?.get(templateInfo.second)
        }

        fun getOriginalSize() = completeSequence.size

        fun getSize(): Int {
            if (abstract) return template.size
            return completeSequence.size
        }

        fun getAll(): List<ImpactsOfAction> {
            if (abstract) return template.values.flatten()
            return completeSequence
        }
    }

}