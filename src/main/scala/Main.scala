import dsl.NetworkImplicits._
import fr.univ_lille.cristal.emeraude.n2s3.core.models.properties.{MembraneLeakTime, MembraneThresholdPotential}
import fr.univ_lille.cristal.emeraude.n2s3.features.builder.connection.types.FullConnection
import fr.univ_lille.cristal.emeraude.n2s3.features.io.input.N2S3InputStreamCombinators._
import fr.univ_lille.cristal.emeraude.n2s3.features.io.input.{MnistFileInputStream, _}
import fr.univ_lille.cristal.emeraude.n2s3.models.bio.{InhibitorConnection, LIFNeuron, Synapse}
import fr.univ_lille.cristal.emeraude.n2s3.support.UnitCast._
import squants.electro.ElectricPotentialConversions.ElectricPotentialConversions
import squants.time.Hertz

import scala.util.Random

object Main extends App {

  /**
    * Declarative part: define the network
    */
  implicit val network = new Network[SampleUnitInput, SampleInput]
  network withInputNeuronGroup "input"
  network withNeuronGroup "hidden" ofSize 40 modeling LIFNeuron withParameters (
    MembraneThresholdPotential -> 1.millivolts,
    MembraneLeakTime -> 20.MilliSecond
  )
  network withNeuronGroup "output" ofSize 10 modeling LIFNeuron withParameters (
    (MembraneThresholdPotential, 1 millivolts)
    )

  "input" connectTo "hidden" using new FullConnection(() => new Synapse(Random.nextFloat * 0.1f))
  "hidden" connectTo "output" using new FullConnection(() => new Synapse(Random.nextFloat * 0.1f))

  "hidden" connectTo "hidden" using new FullConnection(() => new InhibitorConnection)
  "output" connectTo "output" using new FullConnection(() => new InhibitorConnection)


  /**
    * Imperative part: start the network
    */
  val inputStream = MnistEntry() >> StreamOversampling(1, 1) >> SampleToSpikeTrainConverter(0, 50, 35 MilliSecond, 25 MilliSecond) >> InputNoiseGenerator(Hertz(50))
  network.hasInput(inputStream, new MnistFileInputStream("/home/omariott/Documents/M2/Stage/MNIST/train-images.idx3-ubyte", "/home/omariott/Documents/M2/Stage/MNIST/train-labels.idx1-ubyte").shuffle() take 10)

  registerBenchmarkMonitorOn("hidden")

  makeSynapsesWeightGraph("input", "hidden")

  val n2s3 = network.toN2S3

  //Should be moved up, to the declarative part
  //n2s3.addNetworkObserver(new SynapsesWeightGraphBuilderRef("input", "hidden"))
  //n2s3.addNetworkObserver(new SynapsesWeightGraphBuilderRef("input", "output"))

  n2s3.runAndWait()

  /**
    * Imperative part: Prepare the network for testing
    */
  fixWeights("hidden")
  fixWeights("output")

  /**
    * Should be declarative: Declare testing set
    */
  inputStream.clean()
  inputStream.append(new MnistFileInputStream("/home/omariott/Documents/M2/Stage/MNIST/t10k-images.idx3-ubyte", "/home/omariott/Documents/M2/Stage/MNIST/t10k-labels.idx1-ubyte") take 10)

  //val benchmarkMonitor = network addObserver new BenchmarkMonitorRef("hidden")
  //val benchmarkMonitor = n2s3.createBenchmarkMonitor("hidden")

  /**
    * Imperative part: start the network for testing
    */
  n2s3.runAndWait()

  val benchmarkMonitor = getBenchmarkObserverOn("hidden")
  val result = benchmarkMonitor.getResult
  println("res hidden = " + result.evaluationByMaxSpiking.score + "/" + result.inputNumber)
  benchmarkMonitor.exportToHtmlView("res.html")
  println("Finished!")
}