import dsl.NetworkImplicits._
import fr.univ_lille.cristal.emeraude.n2s3.features.builder.connection.types.FullConnection
import fr.univ_lille.cristal.emeraude.n2s3.features.io.input.N2S3InputStreamCombinators._
import fr.univ_lille.cristal.emeraude.n2s3.features.io.input.{MnistFileInputStream, _}
import fr.univ_lille.cristal.emeraude.n2s3.features.io.report.BenchmarkMonitorRef
import fr.univ_lille.cristal.emeraude.n2s3.features.logging.graph.SynapsesWeightGraphBuilderRef
import fr.univ_lille.cristal.emeraude.n2s3.models.qbg.{QBGInhibitorConnection, QBGNeuron, QBGNeuronConnectionWithNegative}
import fr.univ_lille.cristal.emeraude.n2s3.support.UnitCast._


object Main extends App {

  /**
    * Declarative part: define the network
    */
  implicit val network = new Network[SampleUnitInput, SampleInput]
  network withInputNeuronGroup "input"
  network withNeuronGroup "hidden" ofSize 40 modeling QBGNeuron
  network withNeuronGroup "output" ofSize 10 modeling QBGNeuron

  "input" connectTo "hidden" using new FullConnection(() => new QBGNeuronConnectionWithNegative)
  "hidden" connectTo "output" using new FullConnection(() => new QBGNeuronConnectionWithNegative)

  "hidden" connectTo "hidden" using new FullConnection(() => new QBGInhibitorConnection)
  "output" connectTo "output" using new FullConnection(() => new QBGInhibitorConnection)


  /**
    * Imperative part: start the network
    */
  val inputStream = MnistEntry() >> StreamOversampling(1, 1) >> SampleToSpikeTrainConverter(0, 50, 350 MilliSecond, 250 MilliSecond)// >> InputNoiseGenerator(Hertz(50))
  network.taking(inputStream, new MnistFileInputStream("/home/omariott/Documents/M2/Stage/MNIST/train-images.idx3-ubyte","/home/omariott/Documents/M2/Stage/MNIST/train-labels.idx1-ubyte").shuffle() .take(4000))

  val n2s3 = network.toN2S3()

  //Should be moved up, to the declarative part
  n2s3.addNetworkObserver(new SynapsesWeightGraphBuilderRef("input", "hidden"))

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
  inputStream.append(new MnistFileInputStream("/home/omariott/Documents/M2/Stage/MNIST/t10k-images.idx3-ubyte","/home/omariott/Documents/M2/Stage/MNIST/t10k-labels.idx1-ubyte")  take 100)

  val benchmarkMonitor = network addObserver new BenchmarkMonitorRef("hidden")


  /**
    * Imperative part: start the network for testing
    */
  n2s3.runAndWait()

  val result = benchmarkMonitor.getResult
  println("res hidden = "+result.evaluationByMaxSpiking.score+"/"+result.inputNumber)
  benchmarkMonitor.exportToHtmlView("res.html")
  println("Finished!")
}