package org.evomaster.resource.rest.generator

import org.evomaster.resource.rest.generator.implementation.java.dependency.ConditionalDependencyKind
import org.evomaster.resource.rest.generator.model.RestMethod
import org.evomaster.resource.rest.generator.model.StrategyNameResource
import kotlin.math.roundToInt

/**
 * created by manzh on 2019-12-19
 */

class ResExp{

    fun desGenConfig() {

        val nodes = (1..10).map { it * 5 }

        val inproperty = 2
        val nnproperty = 4
        val branchForinproperty = 25

        for (n in nodes){

        }
    }

    fun setDependency(numOfNode : Int, config: GenConfig, type: DependencyType){
        val numOfEdge = type.density * numOfNode * (numOfNode - 1)

        // 1:1 -> 50%, 2:1 -> 20%, 1:2->20%, 1:3 ->10%, 3:1->10%, 2:2 -> 10%
        config.numOfOneToOne = (numOfEdge * 0.5).toInt()
        config.numOfOneToTwo = (numOfEdge * 0.2 /2).roundToInt()
        config.numOfTwoToOne = (numOfEdge * 0.2 /2).roundToInt()
        config.numOfOneToMany = (numOfEdge * 0.2 /3).roundToInt()
        config.numOfManyToOne = (numOfEdge * 0.2 /3).roundToInt()
        config.numOfTwoToTwo = (numOfEdge * 0.1).roundToInt()

        val total = config.numOfOneToOne + config.numOfOneToTwo * 2 + config.numOfTwoToOne *2 + config.numOfOneToMany * 3 + config.numOfManyToOne * 3 + config.numOfTwoToTwo * 4
        if(total > numOfEdge){
            val removal = total - numOfEdge
        }
    }
    //density = e/(v(v-1)), 0.25, 0.5,0.75
    enum class DependencyType (val density : Double){
        NONE (0.0),
        SPARSE (0.25),
        MEDIUM(0.5),
        DENSE(0.75)
    }
}

fun main(args : Array<String>){
    val config = GenConfig()

    config.numOfNodes = 5

    /*val exp = ResExp()
    exp.setDependency(5, config, ResExp.DependencyType.SPARSE)*/
    config.numOfOneToOne = 3

    config.outputType = GenConfig.OutputType.MAVEN_PROJECT
    config.outputContent = GenConfig.OutputContent.CS_EM_EX
    //config.dependencyKind = ConditionalDependencyKind.PROPERTY
    config.nameStrategy = StrategyNameResource.RAND_FIXED_LENGTH

    //config.projectName = "rest-dep-exi-cs-RM${config.restMethods.size}-N${config.numOfNodes}-1To1${config.numOfOneToOne}"
    config.projectName ="try2"
    GenerateREST(config).run()
}