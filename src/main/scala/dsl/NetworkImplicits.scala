/**
  * Created by omariott on 16/02/17.
  */

package dsl

import java.util.NoSuchElementException

import fr.univ_lille.cristal.emeraude.n2s3.core.{NeuronModel, Property}
import fr.univ_lille.cristal.emeraude.n2s3.features.builder.connection.ConnectionPolicy
import fr.univ_lille.cristal.emeraude.n2s3.features.builder.{N2S3, NeuronGroupObserverRef, NeuronGroupRef}
import fr.univ_lille.cristal.emeraude.n2s3.features.io.input.{SampleInput, SampleUnitInput, StreamSupport}
import fr.univ_lille.cristal.emeraude.n2s3.features.logging.graph.SynapsesWeightGraphBuilderRef
import fr.univ_lille.cristal.emeraude.n2s3.models.qbg.QBGNeuron
import fr.univ_lille.cristal.emeraude.n2s3.support.io.{Input, InputSeq, InputStream, N2S3Input}

import scala.collection.mutable

object NetworkImplicits {

  implicit def stringToConnectionBuilder(id: String)(implicit network: Network[SampleUnitInput, SampleInput]): ConnectionBuilder = new ConnectionBuilder(id, network)

  class ConnectionBuilder(originId: String, network: Network[SampleUnitInput, SampleInput]) {
    var destinationId: String = _

    def connectTo(destinationId: String): ConnectionBuilder = {
      this.destinationId = destinationId
      this
    }

    def using(connectionType: ConnectionPolicy): Unit = {
      network.getNeuronGroupById(originId)
        .addNeighbour(this.destinationId, connectionType)
      network.getNeuronGroupRef(this.originId).connectTo(network.getNeuronGroupRef(this.destinationId), connectionType)
    }
  }

  implicit def stringToNeuronGroupRef(id: String)(implicit network: Network[SampleUnitInput, SampleInput]): NeuronGroupRef = {
    network.getNeuronGroupRef(id)
  }

  implicit def stringToNeuronGroup(id: String)(implicit network: Network[SampleUnitInput, SampleInput]): Module = {
    try{
      network.getNeuronGroupById(id)
    }
    catch {
      case nse: NoSuchElementException => basicNeuronGroup(id)
    }
  }
  def fixWeights(neuronGroupRef: NeuronGroupRef): Unit = {
    neuronGroupRef.fixNeurons()
  }

  def registerBenchmarkMonitorOn(id: String)(implicit network: Network[SampleUnitInput, SampleInput]): Unit = {
    network.addBenchmarkObserver(id)
  }

  def observeConnectionsBetween(originId: String, destinationId: String)(implicit network: Network[SampleUnitInput, SampleInput]): SynapsesWeightGraphBuilderRef = {
    network.addSynapsesWeightGraph(originId, destinationId)
    network.toN2S3.addNetworkObserver(new SynapsesWeightGraphBuilderRef(originId, destinationId))
  }

  class Module(identifier: String) {


    val id: String = identifier
    private var neuronParameters: Seq[(Property[_], _)] = Seq()
    var neuronsCount = 1
    private var neuronModel: NeuronModel = QBGNeuron
    private var neighbours: Seq[(String, ConnectionPolicy)] = Seq()
    private var isInput: Boolean = false


    def ofSize(size: Int): Module = {
      this.neuronsCount = size
      this
    }

    def modeling(model: NeuronModel): Module = {
      this.neuronModel = model
      this
    }

    def withParameters(properties: (Property[_], _)*): Module = {
      this.neuronParameters = properties
      this
    }

    def makeInput(): Module = {
      this.isInput = true
      this
    }

    def addNeighbour(id: String, connection: ConnectionPolicy): Unit = {
      this.neighbours = this.neighbours :+ (id, connection)
    }

    def toInputNeuronGroup[U <: Input, T <: InputSeq[U]](n2s3: N2S3, input: StreamSupport[T, InputSeq[N2S3Input]]): (String, NeuronGroupRef) = {
      val inputModule = n2s3.createInput(input)
      (this.id, inputModule)
    }


    def toNeuronGroup(n2s3: N2S3): (String, NeuronGroupRef) = {
      (this.id, n2s3.createNeuronGroup(this.id, this.neuronsCount).setNeuronModel(this.neuronModel, this.neuronParameters))
    }
  }

  class Network[U <: Input, T <: InputSeq[U]]() {


    private val n2s3 = new N2S3

    private var modules: Map[String, Module] = Map()
    private var benchmarkedModules: mutable.MutableList[String] = new mutable.MutableList[String]()
    private var graphedConnections: mutable.MutableList[(String, String)] = new mutable.MutableList[(String, String)]()
    private var inputStream: Option[StreamSupport[T, InputSeq[N2S3Input]]] = None
    private val neuronGroups: collection.mutable.Map[String, NeuronGroupRef] = collection.mutable.Map()
    private val activeObservers: collection.mutable.Map[String, NeuronGroupObserverRef] = collection.mutable.Map()


    def addInputModule(module: Module): Module = {
      this.modules += (module.id -> module)
      val neuronGroup = module.toInputNeuronGroup[U, T](n2s3, this.inputStream.get)
      this.neuronGroups.put(neuronGroup._1, neuronGroup._2)
      module
    }

    def withNeuronGroup(module: Module): Unit = {
      this.modules += (module.id -> module)
      val neuronGroup = module.toNeuronGroup(n2s3)
      this.neuronGroups.put(neuronGroup._1, neuronGroup._2)
    }

    def withInputNeuronGroup(id: String): Module = this.addInputModule(inputNeuronGroup(id))

    def hasInput(input: StreamSupport[T, InputSeq[N2S3Input]]): Network[U, T] = {
      this.inputStream = Some(input)
      this
    }

    implicit def toN2S3: N2S3 = n2s3
    /**
    this.modules.values.foreach(_.connect(this.neuronGroups))
      this.benchmarkedModules.foreach(o => this.activeBenchmarkObservers.put(o, n2s3.createBenchmarkMonitor(this.getLayer(o))))
      this.graphedConnections.foreach(pair => n2s3.createSynapseWeightGraphOn(this.getLayer(pair._1), this.getLayer(pair._2)))

      */

    def getNeuronGroupById(id: String): Module = {
      this.modules(id)
    }

    def getNeuronGroupRef(ident: String): NeuronGroupRef = {
      this.neuronGroups(ident)
    }

    def addSynapsesWeightGraph(originId: String, destinationId: String): Unit = {
      this.graphedConnections += originId -> destinationId
    }

    def addBenchmarkObserver(id: String): Unit = {
      this.benchmarkedModules += id
    }

    def addObserver[V <: NeuronGroupObserverRef](ref: V): V = n2s3.addNetworkObserver(ref)

    //    def getBenchmarkObserver(id: String): BenchmarkMonitorRef = {
    //    this.activeBenchmarkObservers(id)
    //}

    def trainOn(stream : InputStream[T]): Unit ={
      val networkStream = this.inputStream.get
      networkStream.clean()
      networkStream.append(stream)
      this.n2s3.runAndWait()
    }

    def testOn(stream : InputStream[T]): Unit ={
      val networkStream = this.inputStream.get
      networkStream.clean()
      networkStream.append(stream)
      this.n2s3.layers.foreach(G => G.fixNeurons())
      this.n2s3.runAndWait()
    }

    def getObserverOn(s: String): NeuronGroupObserverRef = {
      this.activeObservers(s)
    }
  }

  def basicNeuronGroup(id: String): Module = new Module(id)

  def inputNeuronGroup(id: String): Module = new Module(id).makeInput()
}
