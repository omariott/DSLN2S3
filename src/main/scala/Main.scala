import akka.actor.Props
import dsl.NetworkImplicits._
import fr.univ_lille.cristal.emeraude.n2s3.core.ExternalSender
import fr.univ_lille.cristal.emeraude.n2s3.features.builder.connection.types.FullConnection
import fr.univ_lille.cristal.emeraude.n2s3.features.io.input.N2S3InputStreamCombinators._
import fr.univ_lille.cristal.emeraude.n2s3.features.io.input.{MnistFileInputStream, _}
import fr.univ_lille.cristal.emeraude.n2s3.features.logging.NeuronsPotentialLogText
import fr.univ_lille.cristal.emeraude.n2s3.models.qbg.{NeuronPotentialEvent, QBGInhibitorConnection, QBGNeuron}
import fr.univ_lille.cristal.emeraude.n2s3.support.UnitCast._
import fr.univ_lille.cristal.emeraude.n2s3.support.actors.LocalActorDeploymentStrategy
import fr.univ_lille.cristal.emeraude.n2s3.support.event.Subscribe


object main extends App {

  val inputStream = MnistEntry() >> StreamOversampling(1, 1) >> SampleToSpikeTrainConverter(0, 50, 350 MilliSecond, 250 MilliSecond)// >> InputNoiseGenerator(Hertz(50))
  

  implicit val network = new Network[SampleUnitInput, SampleInput]

  network withInputNeuronGroup "input"
  network withNeuronGroup "hidden" ofSize 40 modeling QBGNeuron
  network withNeuronGroup "output" ofSize 10 modeling QBGNeuron

  "hidden" connectTo "hidden" using new FullConnection(() => new QBGInhibitorConnection)


  //  myNetwork links "input" using new FullConnection(() => new QBGNeuronConnectionWithNegative) to "hidden"
  network links "hidden" using new FullConnection(() => new QBGInhibitorConnection) to "hidden"
  //  myNetwork links "hidden" using new FullConnection(() => new QBGNeuronConnectionWithNegative) to "output"
  network links "output" using new FullConnection(() => new QBGInhibitorConnection) to "output"



  network.taking(inputStream, new MnistFileInputStream("/home/omariott/Documents/M2/Stage/MNIST/train-images.idx3-ubyte","/home/omariott/Documents/M2/Stage/MNIST/train-labels.idx1-ubyte").shuffle() .take(4000))
  val n2s3 = network.toN2S3()
  val inputLayer = network getLayer "input"
  val unsupervisedLayer = network getLayer "hidden"
  val outputLayer = network getLayer "output"


  n2s3.create()
//  println(n2s3.inputLayerRef)
//  println(ExternalSender.askTo(unsupervisedLayer.neuronPaths.head, GetAllConnectionProperty(SynapseWeightAndDelay)))
//  println(inputLayer.neuronPaths)
//  println(unsupervisedLayer.neuronPaths)


  val logger = n2s3.system.actorOf(Props(new NeuronsPotentialLogText("NP.txt")), LocalActorDeploymentStrategy)
  ExternalSender.askTo(outputLayer.neuronPaths.head, Subscribe(NeuronPotentialEvent, ExternalSender.getReference(logger)))
  n2s3.createSynapseWeightGraphOn(unsupervisedLayer, outputLayer)
  ExternalSender.askTo(unsupervisedLayer.neuronPaths.head, Subscribe(NeuronPotentialEvent, ExternalSender.getReference(logger)))
  n2s3.createSynapseWeightGraphOn(inputLayer, unsupervisedLayer)
  n2s3.runAndWait()


  unsupervisedLayer.fixNeurons()
  outputLayer.fixNeurons()
  inputStream.clean()
  inputStream.append(new MnistFileInputStream("/home/omariott/Documents/M2/Stage/MNIST/t10k-images.idx3-ubyte","/home/omariott/Documents/M2/Stage/MNIST/t10k-labels.idx1-ubyte")  take 100)
  val benchmarkMonitor = n2s3.createBenchmarkMonitor(unsupervisedLayer)
  val benchmarkMonitorOut = n2s3.createBenchmarkMonitor(outputLayer)
  n2s3.runAndWait()
  val result = benchmarkMonitor.getResult
  println("res hidden = "+result.evaluationByMaxSpiking.score+"/"+result.inputNumber)
  val resultOut = benchmarkMonitorOut.getResult
  println("res Out = "+resultOut.evaluationByMaxSpiking.score+"/"+result.inputNumber)
  benchmarkMonitor.exportToHtmlView("res.html")
  benchmarkMonitorOut.exportToHtmlView("resout.html")
  println("Finished!")
}