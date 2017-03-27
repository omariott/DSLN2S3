/**
  * Created by omariott on 16/02/17.
  */

package dsl

import fr.univ_lille.cristal.emeraude.n2s3.core.{NeuronConnection, NeuronModel}
import fr.univ_lille.cristal.emeraude.n2s3.core.models.properties.MembraneThresholdPotential
import fr.univ_lille.cristal.emeraude.n2s3.features.builder.{ConnectionRef, N2S3, NeuronGroupRef}
import fr.univ_lille.cristal.emeraude.n2s3.features.builder.connection.ConnectionPolicy
import fr.univ_lille.cristal.emeraude.n2s3.features.builder.connection.types.{FullConnection, RandomConnection}
import fr.univ_lille.cristal.emeraude.n2s3.features.io.input.{DigitalHexInputStream, SampleInput, SampleUnitInput, StreamSupport}
import fr.univ_lille.cristal.emeraude.n2s3.models.qbg.{QBGNeuron, QBGNeuronConnectionWithNegative}
import fr.univ_lille.cristal.emeraude.n2s3.support.io.{Input, InputSeq, InputStream, N2S3Input}
import squants.electro.ElectricPotentialConversions.ElectricPotentialConversions

object NetworkImplicits {

   implicit def stringToConnectionBuilder(id:String)(implicit network: Network[SampleUnitInput, SampleInput]): ConnectionBuilder = new ConnectionBuilder(id)

  class ConnectionBuilder(originId: String) {
    def connectTo(destinationId: String) = {
      this
    }
    def using(connectionType: Any) = {

    }
  }

  class Module(identifier: String) {
    val id: String = identifier
    private var neuronsCount = 1
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

    def makeInput(): Module = {
      this.isInput = true
      this
    }

    def addNeighbour(id: String, connection: ConnectionPolicy): Unit = {
      this.neighbours = this.neighbours :+ (id, connection)
    }

    def toNeuronGroup[U <: Input, T <: InputSeq[U]](n2S3: N2S3, input: StreamSupport[T, InputSeq[N2S3Input]], inputStream: InputStream[T]): (String, NeuronGroupRef) = {
      if (this.isInput) {
        val inputModule = n2S3.createInput(input)
        input.append(inputStream)
        (this.id, inputModule)
      }
      else {
        (this.id, n2S3.createNeuronGroup(this.id, this.neuronsCount).setNeuronModel(this.neuronModel, Seq((MembraneThresholdPotential, 100 millivolts))))
      }
    }

    def connectTo(id: String): Unit = {

    }

    def connect(neuronGroups: collection.mutable.Map[String, NeuronGroupRef]) {
      def lookUpAndConnect(param: (String, ConnectionPolicy)): ConnectionRef = {
        neuronGroups(this.id).connectTo(neuronGroups(param._1), param._2)
      }
      this.neighbours.foreach(lookUpAndConnect)
    }

  }

  class Network[U <: Input, T <: InputSeq[U]]() {
    private var modules: Map[String, Module] = Map()
    private var firstModuleToBeLinked: Option[String] = None
    private var secondModuleToBeLinked: Option[String] = None
    private var connectionToBeUsed: ConnectionPolicy = new FullConnection
    private var input: Option[StreamSupport[T, InputSeq[N2S3Input]]] = None
    private var inputStream: Option[InputStream[T]] = None
    private var lastModule: Option[String] = None
    var neuronGroups: collection.mutable.Map[String, NeuronGroupRef] = collection.mutable.Map()

    def has(module: Module): Network[U, T] = {
      addModule(module)
      this
    }

    private def addModule(module: Module) = {
      this.modules += (module.id -> module)
      this.lastModule = Some(module.id)
      module
    }

    def withNeuronGroup(id: String): Module = this.addModule(basicNeuronGroup(id))
    def withInputNeuronGroup(id: String): Module = this.addModule(inputNeuronGroup(id))

    def hasLayer(module: Module) = {
      this.modules += (module.id -> module)
      this.modules(this.lastModule.get).addNeighbour(module.id, new FullConnection(() => new QBGNeuronConnectionWithNegative()))
      this.lastModule = Some(module.id)
      this
    }

    def getNeuronGroupById(id: String) = {
      this.modules(id)
    }

    def neuronGroup(moduleId: String) : Module = {
      this.getNeuronGroupById(moduleId)
    }

    def links(id: String): Network[U, T] = {
      this.firstModuleToBeLinked = Some(id)
      this
    }

    def to(id: String): Network[U, T] = {
      this.secondModuleToBeLinked = Some(id)
      this.connectTemporaries()
      this
    }

    def using(connection: ConnectionPolicy): Network[U, T] = {
      this.connectionToBeUsed = connection
      this
    }

    def connectTemporaries(): Unit = {
      this.modules(this.firstModuleToBeLinked.get).addNeighbour(this.secondModuleToBeLinked.get, this.connectionToBeUsed)
      this.clearTemporaries()
    }

    def clearTemporaries(): Unit = {
      this.firstModuleToBeLinked = None
      this.secondModuleToBeLinked = None
      this.connectionToBeUsed = new FullConnection
    }

    def taking(input: StreamSupport[T, InputSeq[N2S3Input]], inputStream: InputStream[T]): Network[U, T] = {
      this.input = Some(input)
      this.inputStream = Some(inputStream)
      this
    }

    implicit def toN2S3(): N2S3 = {
      val n2s3 = new N2S3

      def putToMap(module: Module) {
        val res = module.toNeuronGroup[U, T](n2s3, this.input.get, this.inputStream.get)
        this.neuronGroups.put(res._1, res._2)
      }

      this.modules.values.foreach(putToMap)
      this.modules.values.foreach(_.connect(this.neuronGroups))
      n2s3
    }

    def getLayer(ident: String): NeuronGroupRef = {
      this.neuronGroups(ident)
    }
  }

  def basicNeuronGroup(id: String): Module = new Module(id)

  def inputNeuronGroup(id: String): Module = new Module(id).makeInput()
}