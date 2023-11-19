package io.ethers.examples.idosniper

import UniswapV2Pair
import UniswapV2Router02
import io.ethers.examples.ConstantsGoerli
import io.ethers.providers.HttpClient
import io.ethers.providers.Provider
import io.ethers.providers.WsClient
import io.ethers.signers.PrivateKeySigner
import java.math.BigInteger

/**
 * This is a dummy implementation of IDO sniper. The program listens to new UniswapV2Pair Mint events which
 * represent adding liquidity. When new add liquidity event is found we immediately execute a buy transaction.
 */
fun main() {
    // Initialize provider for subscription and for sending transactions
    val wsClient = WsClient(ConstantsGoerli.WS_URL)
    val httpClient = HttpClient(ConstantsGoerli.HTTP_URL)
    val wsProvider = Provider(wsClient)
    val httpProvider = Provider(httpClient)

    // Amount of ETH in wei to spend when buying tokens
    val ethAmount = "1000000000000".toBigInteger() // 0,000001

    // Get private key from environment variables and generate a wallet
    val signer = PrivateKeySigner(System.getenv("W1_PRIVATE_KEY"))

    // Get addresses
    val weth = ConstantsGoerli.WETH_ADDR
    val token = ConstantsGoerli.UNI_ADDR
    // Uniswap V2 pool to observe
    val poolAddress = ConstantsGoerli.UNISWAPV2_WETH_UNI_POOL_ADDR

    // Initialise router
    val router = UniswapV2Router02(httpProvider, ConstantsGoerli.UNISWAPV2_ROUTER_ADDR)

    // Use pool contract to subscribe to new mint events and filter incoming mint events for observed pool address.
    // It is also possible to filter based on topics and block numbers.
    val sub = UniswapV2Pair.Mint
        .filter(wsProvider)
        .address(poolAddress)
        .subscribe()
        .sendAwait()
        .resultOrThrow()

    // We use an iterator because we are only interested in the first event
    val iter = sub.iterator()

    // hasNext blocks the calling thread until an event becomes available or the stream has been unsubscribed
    if (iter.hasNext()) {
        println("Add liquidity event found in tx ${iter.next().transactionHash}, executing buy order...")

        // Buy tokens immediately after next (should be first) add liquidity event
        // deadline is 30 minutes
        val deadline = ((System.currentTimeMillis() / 1000) + 1800).toBigInteger()
        val call = router.swapExactETHForTokens(
            BigInteger.ZERO,
            arrayOf(weth, token),
            signer.address,
            deadline,
        )
        val pendingTx = call.value(ethAmount).send(signer).sendAwait().resultOrThrow()

        // Wait for transaction to be included in a block
        val receipt = pendingTx.awaitInclusion(retries = 5).resultOrThrow()
        println("Buy tx ${receipt.transactionHash} was included in block ${receipt.blockNumber}")

        // Close the stream at the end
        sub.unsubscribe()
    }
}
