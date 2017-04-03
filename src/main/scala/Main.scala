import dsl.NetworkImplicits._
import fr.univ_lille.cristal.emeraude.n2s3.core.models.properties.{MembraneLeakTime, MembraneThresholdPotential}
import fr.univ_lille.cristal.emeraude.n2s3.features.builder.connection.types.FullConnection
import fr.univ_lille.cristal.emeraude.n2s3.features.io.input.N2S3InputStreamCombinators._
import fr.univ_lille.cristal.emeraude.n2s3.features.io.input.{MnistFileInputStream, _}
import fr.univ_lille.cristal.emeraude.n2s3.features.io.report.BenchmarkMonitorRef
import fr.univ_lille.cristal.emeraude.n2s3.models.qbg.{QBGInhibitorConnection, QBGNeuron, QBGNeuronConnectionWithNegative}
import fr.univ_lille.cristal.emeraude.n2s3.support.UnitCast._
import squants.electro.ElectricPotentialConversions.ElectricPotentialConversions

object Main extends App {

  val MNISTTrainingSet = "/home/omariott/Documents/M2/Stage/MNIST/train-images.idx3-ubyte"
  val MNISTTrainingLabels = "/home/omariott/Documents/M2/Stage/MNIST/train-labels.idx1-ubyte"
  val MNISTTestSet = "/home/omariott/Documents/M2/Stage/MNIST/t10k-images.idx3-ubyte"
  val MNISTTestLabels = "/home/omariott/Documents/M2/Stage/MNIST/t10k-labels.idx1-ubyte"

  /**
    * Declarative part: define the network
    */
  implicit val network = new Network[SampleUnitInput, SampleInput]

  network hasInput MnistEntry() >> SampleToSpikeTrainConverter(0, 50, 35 MilliSecond, 25 MilliSecond)

  network withInputNeuronGroup "input"
  network withNeuronGroup ("hidden" ofSize 40 modeling QBGNeuron withParameters (
    MembraneThresholdPotential -> 1.millivolts,
    MembraneLeakTime -> 20.MilliSecond
  ))
  network withNeuronGroup ("output" ofSize 10 modeling QBGNeuron withParameters (
    (MembraneThresholdPotential, 1 millivolts)
    ))

  "input" connectTo "hidden" using new FullConnection(() => new QBGNeuronConnectionWithNegative)
  "hidden" connectTo "output" using new FullConnection(() => new QBGNeuronConnectionWithNegative)

  "hidden" connectTo "hidden" using new FullConnection(() => new QBGInhibitorConnection)
  "output" connectTo "output" using new FullConnection(() => new QBGInhibitorConnection)

  val graph = observeConnectionsBetween("input", "output")

  /**
    * Imperative part: start the network
    */
  network trainOn new MnistFileInputStream(MNISTTrainingSet, MNISTTrainingLabels).shuffle()//.take(1000)

  /**
    * Imperative part: Prepare the network for testing
    */
  val benchmarkMonitor = network addObserver new BenchmarkMonitorRef("hidden")

  /**
    * Imperative part: start the network for testing
    */
  network testOn new MnistFileInputStream(MNISTTestSet, MNISTTestLabels)//.take(200)

  val result = benchmarkMonitor.getResult
  println("res hidden = " + result.evaluationByMaxSpiking.score + "/" + result.inputNumber)
  benchmarkMonitor.exportToHtmlView("res.html")
  println("Finished!")
}