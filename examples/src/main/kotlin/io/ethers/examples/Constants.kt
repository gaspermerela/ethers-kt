package io.ethers.examples

import io.ethers.core.types.Address

object ConstantsGoerli {
    const val HTTP_URL = "https://ethereum-goerli.publicnode.com"
    const val WS_URL = "wss://ethereum-goerli.publicnode.com"
    val UNISWAPV2_ROUTER_ADDR = Address("0x7a250d5630B4cF539739dF2C5dAcb4c659F2488D")
    val USDC_ADDR = Address("0xD87Ba7A50B2E7E660f678A895E4B72E7CB4CCd9C")
    val UNI_ADDR = Address("0x1f9840a85d5aF5bf1D1762F925BDADdC4201F984")
    val WETH_ADDR = Address("0xb4fbf271143f4fbf7b91a5ded31805e42b2208d6")
    val UNISWAPV2_WETH_UNI_POOL_ADDR = Address("0x28cee28a7C4b4022AC92685C07d2f33Ab1A0e122")
}

object ConstantsMainnet {
    const val HTTP_URL = "https://ethereum.publicnode.com"
    const val WS_URL = "wss://ethereum.publicnode.com"
}